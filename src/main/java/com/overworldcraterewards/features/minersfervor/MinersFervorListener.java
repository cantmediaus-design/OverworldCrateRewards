package com.overworldcraterewards.features.minersfervor;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.config.ConfigManager;
import com.overworldcraterewards.data.PDCKeys;
import com.overworldcraterewards.economy.EconomyManager;
import com.overworldcraterewards.hooks.EconomyShopGUIHook;
import com.overworldcraterewards.items.CustomItemType;
import com.overworldcraterewards.util.InventoryUtil;
import com.overworldcraterewards.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the Miner's Fervor pickaxe that grants economy bonus and mining speed streaks.
 *
 * Mechanics:
 * - Passive: +$0.10 per block mined (uses EconomyShopGUI prices for ores if available)
 * - Active: Mining streak system with +0.1% mining speed per streak point (no cap)
 * - Decay: -10% of current streak every 10 seconds of inactivity (rounds down)
 *
 * Easter Egg: At 1,000,000 peak streak, upgrades to "Miner's Obsession" with:
 * - Halved decay rate (5% instead of 10%)
 * - Instant block pickup (items go directly to inventory)
 */
public class MinersFervorListener implements Listener {

    private final OverworldCrateRewardsPlugin plugin;
    private final ConfigManager config;
    private final EconomyManager economy;

    // Session-based streak tracking (per player UUID)
    private final Map<UUID, StreakData> playerStreaks = new ConcurrentHashMap<>();

    // Decay task tracking
    private final Map<UUID, BukkitTask> decayTasks = new ConcurrentHashMap<>();

    // Attribute modifier key for mining speed
    private final NamespacedKey miningSpeedKey;

    public MinersFervorListener(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.economy = plugin.getEconomyManager();
        this.miningSpeedKey = new NamespacedKey(plugin, "miners_fervor_speed");
    }

    // ==================== BLOCK BREAK HANDLING ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        // Check if using Miner's Fervor
        if (!InventoryUtil.isCustomItem(tool, CustomItemType.MINERS_FERVOR)) {
            return;
        }

        boolean upgraded = isUpgradedFervor(tool);

        // Upgraded perk: instant pickup - add drops directly to inventory
        if (upgraded) {
            Block block = event.getBlock();
            Collection<ItemStack> drops = block.getDrops(tool, player);

            // Cancel normal drops
            event.setDropItems(false);

            // Add drops directly to inventory, drop overflow on ground
            for (ItemStack drop : drops) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(drop);
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }

            // Also handle XP
            int xp = event.getExpToDrop();
            if (xp > 0) {
                player.giveExp(xp);
                event.setExpToDrop(0);
            }
        }

        // Process the block break (streak, economy, etc.)
        processBlockBreak(player, tool, event.getBlock().getType());
    }

    /**
     * Process a block break: update streak, grant economy bonus, apply mining speed.
     */
    private void processBlockBreak(Player player, ItemStack fervor, Material blockType) {
        UUID playerId = player.getUniqueId();
        boolean upgraded = isUpgradedFervor(fervor);

        // 1. Calculate and grant economy bonus
        double bonus = calculateBlockBonus(player, blockType, upgraded);
        economy.deposit(player, bonus);

        // 2. Update streak
        StreakData streak = playerStreaks.computeIfAbsent(playerId, k -> new StreakData());
        streak.incrementStreak();

        // 3. Schedule/reset decay timer
        scheduleDecay(player, fervor, upgraded);

        // 4. Apply mining speed attribute based on streak
        applyMiningSpeedAttribute(player, streak.currentStreak);

        // 5. Play sound feedback (pitch scales with streak)
        playStreakSound(player, streak.currentStreak);

        // 6. Update persistent stats
        updateFervorStats(player, fervor, bonus, streak.currentStreak);

        // 7. Display action bar
        displayStreakInfo(player, streak.currentStreak, bonus);
    }

    // ==================== ECONOMY INTEGRATION ====================

    /**
     * Calculate the economy bonus for breaking a block.
     * Uses EconomyShopGUI prices for valuable ores if available.
     */
    private double calculateBlockBonus(Player player, Material blockType, boolean upgraded) {
        double baseBonus = config.getMinersFervorBonusPerBlock();

        // Try EconomyShopGUI for ore prices (optional enhancement)
        if (EconomyShopGUIHook.isAvailable() && isValuableOre(blockType)) {
            Double shopPrice = EconomyShopGUIHook.getSellPrice(player, blockType, 1);
            if (shopPrice != null && shopPrice > 0) {
                // Use a fraction of shop price as bonus (configurable multiplier)
                return shopPrice * config.getMinersFervorShopPriceMultiplier();
            }
        }

        return baseBonus;
    }

    /**
     * Check if a material is a valuable ore that should use EconomyShopGUI prices.
     */
    private boolean isValuableOre(Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 NETHER_GOLD_ORE, NETHER_QUARTZ_ORE,
                 ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    // ==================== MINING SPEED (ATTRIBUTE-BASED) ====================

    /**
     * Apply mining speed bonus using attribute modifiers instead of potion effects.
     * This avoids conflicts with beacons and other Haste sources.
     * Each streak point = +0.1% mining speed (0.001 multiplier).
     */
    private void applyMiningSpeedAttribute(Player player, long streak) {
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute == null) {
            return;
        }

        // Remove existing modifier if present
        AttributeModifier existingModifier = null;
        for (AttributeModifier mod : attribute.getModifiers()) {
            if (mod.getKey().equals(miningSpeedKey)) {
                existingModifier = mod;
                break;
            }
        }
        if (existingModifier != null) {
            attribute.removeModifier(existingModifier);
        }

        // Add new modifier if streak > 0
        if (streak > 0) {
            double speedMultiplier = streak * config.getMinersFervorSpeedMultiplierPerPoint();

            AttributeModifier modifier = new AttributeModifier(
                    miningSpeedKey,
                    speedMultiplier,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1
            );
            attribute.addModifier(modifier);
        }
    }

    /**
     * Remove the mining speed attribute modifier from a player.
     */
    private void removeMiningSpeedAttribute(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute == null) {
            return;
        }

        for (AttributeModifier mod : attribute.getModifiers()) {
            if (mod.getKey().equals(miningSpeedKey)) {
                attribute.removeModifier(mod);
                break;
            }
        }
    }

    // ==================== DECAY SYSTEM ====================

    /**
     * Schedule or reset the decay timer for a player's streak.
     */
    private void scheduleDecay(Player player, ItemStack fervor, boolean upgraded) {
        UUID playerId = player.getUniqueId();

        // Cancel existing decay task
        BukkitTask existingTask = decayTasks.remove(playerId);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Schedule new decay
        long decayInterval = config.getMinersFervorDecayIntervalTicks();
        double decayRate = upgraded ?
                config.getMinersFervorDecayRate() / 2.0 : // Halved for upgraded
                config.getMinersFervorDecayRate();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            StreakData streak = playerStreaks.get(playerId);
            if (streak == null || !player.isOnline()) {
                cancelDecayTask(playerId);
                removeMiningSpeedAttribute(player);
                return;
            }

            // Check if player is still inactive
            long timeSinceLastMine = System.currentTimeMillis() - streak.lastMineTime;
            if (timeSinceLastMine >= (decayInterval * 50)) { // Convert ticks to ms
                long oldStreak = streak.currentStreak;
                streak.applyDecay(decayRate, config.getMinersFervorDecayFloor());

                // Update mining speed attribute
                applyMiningSpeedAttribute(player, streak.currentStreak);

                // Update action bar to show new streak
                if (streak.currentStreak > 0) {
                    displayStreakInfo(player, streak.currentStreak, 0);
                }

                // Update item lore if player is holding the fervor
                ItemStack heldItem = player.getInventory().getItemInMainHand();
                if (InventoryUtil.isCustomItem(heldItem, CustomItemType.MINERS_FERVOR)) {
                    updateFervorLore(heldItem, streak.currentStreak);
                    player.getInventory().setItemInMainHand(heldItem);
                }

                // Remove speed bonus and cancel task if streak dropped to 0
                if (streak.currentStreak == 0) {
                    removeMiningSpeedAttribute(player);
                    cancelDecayTask(playerId);
                    // Clear action bar
                    player.sendActionBar(Component.text("Streak lost!", NamedTextColor.RED));
                }
            }
        }, decayInterval, decayInterval);

        decayTasks.put(playerId, task);
    }

    /**
     * Cancel a player's decay task.
     */
    private void cancelDecayTask(UUID playerId) {
        BukkitTask task = decayTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    // ==================== SOUND FEEDBACK ====================

    /**
     * Play sound feedback based on streak level.
     * Pitch increases with streak (capped at valid range).
     */
    private void playStreakSound(Player player, long streak) {
        // Map streak to pitch (0.5 to 2.0 range)
        // Low streak = lower pitch, high streak = higher pitch
        float basePitch = 0.5f;
        float pitchIncrement = 0.0015f; // Gentle increase
        float pitch = Math.min(2.0f, basePitch + (streak * pitchIncrement));

        player.playSound(
                player.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                0.3f, // Quiet volume
                pitch
        );

        // Milestone sounds at specific thresholds
        if (streak == 100 || streak == 500 || streak == 1000 ||
                (streak > 0 && streak % 5000 == 0)) {
            player.playSound(player.getLocation(),
                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        }
    }

    // ==================== DISPLAY ====================

    /**
     * Display streak info on action bar.
     */
    private void displayStreakInfo(Player player, long streak, double earned) {
        double speedBonus = streak * config.getMinersFervorSpeedMultiplierPerPoint() * 100;
        Component message = Component.text("Streak: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.formatNumber(streak), NamedTextColor.AQUA))
                .append(Component.text(" | Speed: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("+%.1f%%", speedBonus), NamedTextColor.GREEN));

        if (earned > 0) {
            message = message.append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("+" + MessageUtil.formatCurrency(earned), NamedTextColor.GOLD));
        }

        player.sendActionBar(message);
    }

    // ==================== STAT TRACKING ====================

    /**
     * Update Miner's Fervor stats in PDC and check for upgrade threshold.
     */
    private void updateFervorStats(Player player, ItemStack fervor, double earned, long currentStreak) {
        if (fervor == null || !fervor.hasItemMeta()) {
            return;
        }

        ItemMeta meta = fervor.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Update blocks mined
        long blocks = pdc.getOrDefault(PDCKeys.MINERS_FERVOR_BLOCKS, PersistentDataType.LONG, 0L);
        pdc.set(PDCKeys.MINERS_FERVOR_BLOCKS, PersistentDataType.LONG, blocks + 1);

        // Update earned
        double totalEarned = pdc.getOrDefault(PDCKeys.MINERS_FERVOR_EARNED, PersistentDataType.DOUBLE, 0.0);
        pdc.set(PDCKeys.MINERS_FERVOR_EARNED, PersistentDataType.DOUBLE, totalEarned + earned);

        // Update peak streak if necessary
        long peakStreak = pdc.getOrDefault(PDCKeys.MINERS_FERVOR_PEAK_STREAK, PersistentDataType.LONG, 0L);
        if (currentStreak > peakStreak) {
            pdc.set(PDCKeys.MINERS_FERVOR_PEAK_STREAK, PersistentDataType.LONG, currentStreak);
            peakStreak = currentStreak;
        }

        fervor.setItemMeta(meta);

        // Check for upgrade
        if (!isUpgradedFervor(fervor) && peakStreak >= config.getMinersFervorUpgradeThreshold()) {
            upgradeFervor(fervor, player);
        }

        // Update lore
        updateFervorLore(fervor, currentStreak);

        // Update in inventory
        player.getInventory().setItemInMainHand(fervor);
    }

    /**
     * Check if a Miner's Fervor has been upgraded.
     */
    private boolean isUpgradedFervor(ItemStack fervor) {
        if (fervor == null || !fervor.hasItemMeta()) {
            return false;
        }
        return fervor.getItemMeta().getPersistentDataContainer()
                .has(PDCKeys.MINERS_FERVOR_UPGRADED, PersistentDataType.BOOLEAN);
    }

    // ==================== UPGRADE (EASTER EGG) ====================

    /**
     * Upgrade the Miner's Fervor to Miner's Obsession.
     */
    private void upgradeFervor(ItemStack fervor, Player player) {
        if (fervor == null || !fervor.hasItemMeta()) {
            return;
        }

        ItemMeta meta = fervor.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Mark as upgraded
        pdc.set(PDCKeys.MINERS_FERVOR_UPGRADED, PersistentDataType.BOOLEAN, true);

        // Apply obsession gradient name (deep purple/dark colors)
        meta.displayName(createObsessionGradientName("Miner's Obsession"));

        fervor.setItemMeta(meta);

        // Epic effects
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 0.7f, 1.2f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.5f);

        // Particle explosion (amethyst/crystal theme)
        player.getWorld().spawnParticle(
                Particle.DUST,
                player.getLocation().add(0, 1, 0),
                100,
                1.0, 1.0, 1.0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(148, 0, 211), 1.5f)
        );
        player.getWorld().spawnParticle(
                Particle.END_ROD,
                player.getLocation().add(0, 1, 0),
                50,
                0.5, 0.5, 0.5,
                0.05
        );

        // Epic message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("\u2726 ", NamedTextColor.DARK_PURPLE)
                .append(Component.text("OBSIDIAN AWAKENING", NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.BOLD, true))
                .append(Component.text(" \u2726", NamedTextColor.DARK_PURPLE)));
        player.sendMessage(Component.text("Your mining fervor has become an obsession!", NamedTextColor.AQUA));
        player.sendMessage(Component.text("The Miner's Fervor has become ", NamedTextColor.GRAY)
                .append(createObsessionGradientName("Miner's Obsession"))
                .append(Component.text("!", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("\u2022 Halved streak decay (5% instead of 10%)", NamedTextColor.DARK_AQUA));
        player.sendMessage(Component.text("\u2022 Instant block pickup (items go to inventory)", NamedTextColor.DARK_AQUA));
        player.sendMessage(Component.empty());
    }

    /**
     * Create the obsession gradient name component (purple spectrum).
     */
    private Component createObsessionGradientName(String text) {
        TextColor[] obsessionColors = {
                TextColor.color(75, 0, 130),    // Indigo
                TextColor.color(102, 51, 153),  // Rebecca Purple
                TextColor.color(138, 43, 226),  // Blue Violet
                TextColor.color(148, 0, 211),   // Dark Violet
                TextColor.color(128, 0, 128)    // Purple
        };

        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            TextColor color = obsessionColors[i % obsessionColors.length];
            builder.append(Component.text(c, color)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
        }

        return builder.build();
    }

    // ==================== LORE UPDATE ====================

    /**
     * Update the Miner's Fervor lore to show stats.
     */
    private void updateFervorLore(ItemStack fervor, long currentStreak) {
        if (fervor == null || !fervor.hasItemMeta()) {
            return;
        }

        ItemMeta meta = fervor.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long blocks = pdc.getOrDefault(PDCKeys.MINERS_FERVOR_BLOCKS, PersistentDataType.LONG, 0L);
        double earned = pdc.getOrDefault(PDCKeys.MINERS_FERVOR_EARNED, PersistentDataType.DOUBLE, 0.0);
        long peakStreak = pdc.getOrDefault(PDCKeys.MINERS_FERVOR_PEAK_STREAK, PersistentDataType.LONG, 0L);
        boolean upgraded = pdc.has(PDCKeys.MINERS_FERVOR_UPGRADED, PersistentDataType.BOOLEAN);

        List<Component> lore = new ArrayList<>();

        // Upgraded header
        if (upgraded) {
            lore.add(Component.text("\u2726 OBSESSION EDITION \u2726", NamedTextColor.DARK_PURPLE)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Halved decay | Instant pickup", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // Current streak (session-based)
        lore.add(Component.text("Current Streak: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.formatNumber(currentStreak), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));

        // Speed bonus
        double speedBonus = currentStreak * config.getMinersFervorSpeedMultiplierPerPoint() * 100;
        lore.add(Component.text("Speed Bonus: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("+%.1f%%", speedBonus), NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));

        lore.add(Component.empty());

        // Lifetime stats
        lore.add(Component.text("Blocks Mined: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.formatNumber(blocks), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Total Earned: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.formatCurrency(earned), NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Peak Streak: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.formatNumber(peakStreak), NamedTextColor.LIGHT_PURPLE))
                .decoration(TextDecoration.ITALIC, false));

        lore.add(Component.empty());

        // Description
        lore.add(Component.text("Mine blocks to build your streak", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Streak decays when idle", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        fervor.setItemMeta(meta);
    }

    // ==================== ENCHANTMENT PREVENTION ====================

    /**
     * Prevent Miner's Fervor from being enchanted at enchantment table.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem();
        if (InventoryUtil.isCustomItem(item, CustomItemType.MINERS_FERVOR)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent Miner's Fervor from being enchanted at enchantment table (backup).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchant(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        if (InventoryUtil.isCustomItem(item, CustomItemType.MINERS_FERVOR)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent Miner's Fervor from being enchanted/combined at anvil.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack firstItem = event.getInventory().getFirstItem();
        ItemStack secondItem = event.getInventory().getSecondItem();

        // If either item is a Miner's Fervor, prevent the operation
        if (InventoryUtil.isCustomItem(firstItem, CustomItemType.MINERS_FERVOR) ||
                InventoryUtil.isCustomItem(secondItem, CustomItemType.MINERS_FERVOR)) {
            event.setResult(null);
        }
    }

    // ==================== CLEANUP ====================

    /**
     * Clean up player data on quit.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        playerStreaks.remove(playerId);
        cancelDecayTask(playerId);
        removeMiningSpeedAttribute(player);
    }

    /**
     * Clean up all tasks (called on plugin disable).
     */
    public void cleanup() {
        for (UUID playerId : decayTasks.keySet()) {
            cancelDecayTask(playerId);
        }
        playerStreaks.clear();
    }

    // ==================== DEBUG API ====================

    /**
     * Set a player's current streak (for debug purposes).
     * @param player The player
     * @param streak The streak value to set
     */
    public void setPlayerStreak(Player player, long streak) {
        UUID playerId = player.getUniqueId();
        StreakData data = playerStreaks.computeIfAbsent(playerId, k -> new StreakData());
        data.currentStreak = streak;
        data.lastMineTime = System.currentTimeMillis();

        // Apply mining speed attribute for the new streak
        applyMiningSpeedAttribute(player, streak);

        // Start decay if not already running
        ItemStack fervor = player.getInventory().getItemInMainHand();
        if (InventoryUtil.isCustomItem(fervor, CustomItemType.MINERS_FERVOR)) {
            scheduleDecay(player, fervor, isUpgradedFervor(fervor));
            updateFervorLore(fervor, streak);
            player.getInventory().setItemInMainHand(fervor);
        }

        // Show action bar
        displayStreakInfo(player, streak, 0);
    }

    /**
     * Get a player's current streak.
     * @param player The player
     * @return The current streak value
     */
    public long getPlayerStreak(Player player) {
        StreakData data = playerStreaks.get(player.getUniqueId());
        return data != null ? data.currentStreak : 0;
    }

    // ==================== INNER CLASS ====================

    /**
     * Tracks session-based streak data for a player.
     */
    private static class StreakData {
        long currentStreak;
        long lastMineTime;

        StreakData() {
            this.currentStreak = 0;
            this.lastMineTime = System.currentTimeMillis();
        }

        void incrementStreak() {
            currentStreak++;
            lastMineTime = System.currentTimeMillis();
        }

        void applyDecay(double decayRate, int decayFloor) {
            // Calculate decay: -X% of current streak (rounded down), minimum decayFloor
            // The flat floor prevents low streaks from trickling forever
            long decay = Math.max(decayFloor, (long) Math.floor(currentStreak * decayRate));
            currentStreak = Math.max(0, currentStreak - decay);
        }
    }
}
