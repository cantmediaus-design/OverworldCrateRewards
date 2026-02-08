package com.overworldcraterewards.features.harvesthoe;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.config.ConfigManager;
import com.overworldcraterewards.data.PDCKeys;
import com.overworldcraterewards.economy.EconomyManager;
import com.overworldcraterewards.features.farmerspouch.FarmersPouchListener;
import com.overworldcraterewards.items.CustomItemType;
import com.overworldcraterewards.util.InventoryUtil;
import com.overworldcraterewards.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the Harvest Hoe tool that harvests a 3x3 area and auto-replants.
 * Integrates with Farmer's Pouch for auto-selling harvested crops.
 * Has an innate StatTracker with 10x milestone requirements.
 * Efficiency bonus = % chance to expand to 5x5 radius.
 */
public class HarvestHoeListener implements Listener {

    private final OverworldCrateRewardsPlugin plugin;
    private final ConfigManager config;
    private final EconomyManager economy;
    private final FarmersPouchListener pouchListener;

    // Cooldown tracking
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public HarvestHoeListener(OverworldCrateRewardsPlugin plugin, FarmersPouchListener pouchListener) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.economy = plugin.getEconomyManager();
        this.pouchListener = pouchListener;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only main hand
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        // Check if holding Harvest Hoe
        if (!InventoryUtil.isCustomItem(heldItem, CustomItemType.HARVEST_HOE)) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // Check if clicked on a harvestable crop
        if (!CropHelper.isHarvestableCrop(clickedBlock)) {
            return;
        }

        // Check cooldown
        if (isOnCooldown(player)) {
            return;
        }

        // Cancel the event to prevent normal interaction
        event.setCancelled(true);

        // Set cooldown
        setCooldown(player);

        // Check if player has Farmer's Pouch for auto-sell (and it's not disabled)
        ItemStack pouch = InventoryUtil.findItemInInventory(player, CustomItemType.FARMERS_POUCH);
        boolean autoSellEnabled = pouch != null && !pouchListener.isPouchDisabled(pouch);

        // Check if hoe is upgraded (easter egg - base 5x5)
        boolean isUpgraded = isUpgradedHoe(heldItem);

        // Calculate efficiency bonus for 5x5 radius chance (or 7x7 if upgraded)
        double efficiency = calculateEfficiencyBonus(heldItem);
        boolean expanded = efficiency > 0 && Math.random() * 100 < efficiency;

        // Upgraded hoe: base upgraded radius, expanded +1. Normal: base config radius, expanded +1
        int radius;
        int upgradedRadius = config.getHarvestHoeUpgradedRadius();
        if (isUpgraded) {
            radius = expanded ? (upgradedRadius + 1) : upgradedRadius;
        } else {
            radius = expanded ? 2 : config.getHarvestHoeRadius(); // 2 for 5x5, 1 for 3x3
        }

        // Perform the harvest
        HarvestResult result = harvestArea(player, clickedBlock, heldItem, autoSellEnabled, pouch, radius);

        if (result.harvested > 0) {
            // Update innate stat tracker
            updateCropStats(heldItem, result.harvested, player);
            player.getInventory().setItemInMainHand(heldItem);

            // Play effects
            playHarvestEffects(clickedBlock.getLocation(), player, expanded);

            // Build message
            net.kyori.adventure.text.TextComponent.Builder msgBuilder = Component.text()
                    .append(Component.text("Harvested ", NamedTextColor.GOLD))
                    .append(Component.text(result.harvested, NamedTextColor.WHITE))
                    .append(Component.text(" crops!", NamedTextColor.GOLD));

            if (expanded) {
                String expandedText = isUpgraded ? " (7x7!)" : " (5x5!)";
                msgBuilder.append(Component.text(expandedText, NamedTextColor.LIGHT_PURPLE));
            }

            if (autoSellEnabled && result.totalSold > 0) {
                msgBuilder.append(Component.text(" +" + MessageUtil.formatCurrency(result.totalSold), NamedTextColor.GREEN));
                if (result.luckyCount > 0) {
                    msgBuilder.append(Component.text(" LUCKY x" + result.luckyCount + "!", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.BOLD, true));
                } else {
                    msgBuilder.append(Component.text(" (Auto-Sold)", NamedTextColor.DARK_GREEN));
                }
            }

            player.sendActionBar(msgBuilder.build());
        }
    }

    /**
     * Harvest crops in an area centered on the target block.
     * @param radius 1 for 3x3, 2 for 5x5
     * @param pouch The Farmer's Pouch (may be null if autoSell is false)
     */
    private HarvestResult harvestArea(Player player, Block center, ItemStack tool, boolean autoSell, ItemStack pouch, int radius) {
        int harvested = 0;
        double totalSold = 0.0;
        int luckyCount = 0;

        // Track processed blocks (for sugar cane columns)
        Set<Block> processed = new HashSet<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block block = center.getRelative(dx, 0, dz);

                // Skip if already processed (sugar cane column handling)
                if (processed.contains(block)) {
                    continue;
                }

                // Skip non-crops
                if (!CropHelper.isHarvestableCrop(block)) {
                    continue;
                }

                // Skip crops that aren't fully grown
                if (!CropHelper.isFullyGrown(block)) {
                    continue;
                }

                // Check protection plugins by firing a BlockBreakEvent
                BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
                Bukkit.getPluginManager().callEvent(breakEvent);
                if (breakEvent.isCancelled()) {
                    continue;
                }

                // Mark as processed
                processed.add(block);

                // For sugar cane, mark the whole column
                if (block.getType() == Material.SUGAR_CANE) {
                    markSugarCaneColumn(block, processed);
                }

                // Store block type before harvesting (block will be replaced after harvest)
                Material blockType = block.getType();

                // Harvest and get drops
                Collection<ItemStack> drops = CropHelper.harvestAndReplant(block, player, tool);

                // Increment player statistics for the broken crop block
                try {
                    if (blockType.isBlock()) {
                        player.incrementStatistic(Statistic.MINE_BLOCK, blockType);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Some blocks don't have valid statistics
                }

                // Handle drops - auto-sell if Farmer's Pouch present
                for (ItemStack drop : drops) {
                    if (drop == null || drop.getAmount() <= 0) {
                        continue; // Skip null or empty drops
                    }

                    // Increment player statistics for items picked up
                    try {
                        if (drop.getType().isItem()) {
                            player.incrementStatistic(Statistic.PICKUP, drop.getType(), drop.getAmount());
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Some items don't have valid statistics
                    }

                    if (autoSell && pouch != null) {
                        // Use shared sell service from Farmer's Pouch (applies bonuses + tracks stats)
                        FarmersPouchListener.SellResult sellResult = pouchListener.sellCrop(
                                player, drop.getType(), drop.getAmount(), pouch);

                        if (sellResult != null) {
                            totalSold += sellResult.price();
                            if (sellResult.luckyTriggered()) {
                                luckyCount++;
                            }
                            // Update pouch stats (for upgrade tracking)
                            pouchListener.updatePouchStatsExternal(player, pouch, sellResult.price());
                            continue; // Don't add to inventory
                        }
                    }

                    // Not auto-selling or not a sellable crop - add to inventory
                    HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(drop);
                    // Drop overflow items at player's feet
                    for (ItemStack overflowItem : overflow.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
                    }
                }

                harvested++;
            }
        }

        return new HarvestResult(harvested, totalSold, luckyCount);
    }

    /**
     * Mark all blocks in a sugar cane column as processed.
     */
    private void markSugarCaneColumn(Block block, Set<Block> processed) {
        // Go down to find base
        Block current = block;
        while (current.getRelative(org.bukkit.block.BlockFace.DOWN).getType() == Material.SUGAR_CANE) {
            current = current.getRelative(org.bukkit.block.BlockFace.DOWN);
        }

        // Mark all blocks in column
        while (current.getType() == Material.SUGAR_CANE) {
            processed.add(current);
            current = current.getRelative(org.bukkit.block.BlockFace.UP);
        }
    }

    /**
     * Play harvest visual and sound effects.
     */
    private void playHarvestEffects(Location center, Player player, boolean expanded) {
        // Particle effect - bigger for expanded
        player.getWorld().spawnParticle(
                expanded ? Particle.HAPPY_VILLAGER : Particle.HAPPY_VILLAGER,
                center.clone().add(0.5, 0.5, 0.5),
                expanded ? 30 : 15, // count
                expanded ? 2.5 : 1.5, 0.5, expanded ? 2.5 : 1.5, // offset
                0 // speed
        );

        // Extra particles for expanded harvest
        if (expanded) {
            player.getWorld().spawnParticle(
                    Particle.WITCH,
                    center.clone().add(0.5, 1.0, 0.5),
                    20,
                    2.0, 0.5, 2.0,
                    0
            );
        }

        // Sound effect - different pitch for expanded
        player.playSound(center, Sound.ITEM_CROP_PLANT, 1.0f, expanded ? 1.5f : 1.2f);
        if (expanded) {
            player.playSound(center, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        }
    }

    private boolean isOnCooldown(Player player) {
        Long lastUse = cooldowns.get(player.getUniqueId());
        if (lastUse == null) {
            return false;
        }
        return System.currentTimeMillis() - lastUse < config.getHarvestHoeCooldownMs();
    }

    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Result of a harvest operation.
     */
    private record HarvestResult(int harvested, double totalSold, int luckyCount) {}

    // ==================== INNATE STAT TRACKER ====================

    /**
     * Update crop stats on the Harvest Hoe and check for milestones.
     */
    private void updateCropStats(ItemStack hoe, int cropsHarvested, Player player) {
        if (hoe == null || !hoe.hasItemMeta()) {
            return;
        }

        ItemMeta meta = hoe.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Get current value
        long currentValue = pdc.getOrDefault(PDCKeys.HARVEST_HOE_CROPS, PersistentDataType.LONG, 0L);
        long newValue = currentValue + cropsHarvested;

        // Update value
        pdc.set(PDCKeys.HARVEST_HOE_CROPS, PersistentDataType.LONG, newValue);
        hoe.setItemMeta(meta);

        // Check for milestone
        checkMilestone(player, currentValue, newValue);

        // Update lore
        updateHoeLore(hoe);
    }

    /**
     * Check if a new milestone was reached and notify player.
     */
    private void checkMilestone(Player player, long oldValue, long newValue) {
        List<Long> thresholds = config.getHarvestHoeMilestoneThresholds();
        List<Double> bonuses = config.getHarvestHoeMilestoneBonuses();
        for (int i = 0; i < thresholds.size(); i++) {
            long threshold = thresholds.get(i);
            if (oldValue < threshold && newValue >= threshold) {
                // Milestone reached!
                double bonus = i < bonuses.size() ? bonuses.get(i) : 0;
                String message = "&aHarvest Hoe milestone: &e" + MessageUtil.formatNumber(threshold) +
                        " crops! &7(+" + bonus + "% radius chance)";

                if (threshold >= 1_000_000) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                }

                player.sendMessage(MessageUtil.colorize(config.getMessagePrefix() + message));
            }
        }
    }

    /**
     * Calculate total efficiency bonus from milestones reached.
     */
    private double calculateEfficiencyBonus(ItemStack hoe) {
        if (hoe == null || !hoe.hasItemMeta()) {
            return 0.0;
        }

        ItemMeta meta = hoe.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        long crops = pdc.getOrDefault(PDCKeys.HARVEST_HOE_CROPS, PersistentDataType.LONG, 0L);

        List<Long> thresholds = config.getHarvestHoeMilestoneThresholds();
        List<Double> bonuses = config.getHarvestHoeMilestoneBonuses();
        double totalBonus = 0.0;
        for (int i = 0; i < thresholds.size(); i++) {
            if (crops >= thresholds.get(i)) {
                totalBonus += i < bonuses.size() ? bonuses.get(i) : 0;
            }
        }

        return totalBonus;
    }

    /**
     * Update the Harvest Hoe lore to show crop stats.
     */
    private void updateHoeLore(ItemStack hoe) {
        if (hoe == null || !hoe.hasItemMeta()) {
            return;
        }

        ItemMeta meta = hoe.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        long crops = pdc.getOrDefault(PDCKeys.HARVEST_HOE_CROPS, PersistentDataType.LONG, 0L);
        double efficiency = calculateEfficiencyBonus(hoe);
        boolean upgraded = pdc.has(PDCKeys.HARVEST_HOE_UPGRADED, PersistentDataType.BOOLEAN);

        // Build fresh lore
        List<Component> lore = new ArrayList<>();

        // Show upgraded status
        if (upgraded) {
            lore.add(Component.text("✦ HARDWORKING EDITION ✦", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Base Radius: 5x5 | Expanded: 7x7", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // Stat header
        lore.add(Component.text("Crops Harvested: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.formatNumber(crops), NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));

        // Show efficiency if any
        if (efficiency > 0) {
            String radiusText = upgraded ? "7x7 Radius Chance: " : "5x5 Radius Chance: ";
            lore.add(Component.text(radiusText, NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.0f%%", efficiency), NamedTextColor.LIGHT_PURPLE))
                    .decoration(TextDecoration.ITALIC, false));
        }

        // Show next milestone
        for (long threshold : config.getHarvestHoeMilestoneThresholds()) {
            if (crops < threshold) {
                long remaining = threshold - crops;
                lore.add(Component.text("Next milestone: ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(MessageUtil.formatNumber(remaining) + " more", NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false));
                break;
            }
        }

        // Add blank line and description
        lore.add(Component.empty());
        String baseRadius = upgraded ? "5x5" : "3x3";
        lore.add(Component.text("Right-click crops to harvest " + baseRadius, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Auto-replants and works with Farmer's Pouch", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        hoe.setItemMeta(meta);
    }

    // ==================== ENCHANTMENT PREVENTION ====================

    /**
     * Prevent Harvest Hoe from being enchanted at enchantment table.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem();
        if (InventoryUtil.isCustomItem(item, CustomItemType.HARVEST_HOE)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent Harvest Hoe from being enchanted at enchantment table (backup).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchant(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        if (InventoryUtil.isCustomItem(item, CustomItemType.HARVEST_HOE)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent Harvest Hoe from being enchanted/combined at anvil.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack firstItem = event.getInventory().getFirstItem();
        ItemStack secondItem = event.getInventory().getSecondItem();

        // If either item is a Harvest Hoe, prevent the operation
        if (InventoryUtil.isCustomItem(firstItem, CustomItemType.HARVEST_HOE) ||
            InventoryUtil.isCustomItem(secondItem, CustomItemType.HARVEST_HOE)) {
            event.setResult(null);
        }
    }

    // ==================== DURABILITY EASTER EGG ====================

    /**
     * Check for the 1 durability easter egg trigger.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();

        if (!InventoryUtil.isCustomItem(item, CustomItemType.HARVEST_HOE)) {
            return;
        }

        // Already upgraded? Skip
        if (isUpgradedHoe(item)) {
            return;
        }

        // Check if this damage would bring it to 1 durability or less
        if (item.getItemMeta() instanceof Damageable damageable) {
            int maxDurability = item.getType().getMaxDurability();
            int currentDamage = damageable.getDamage();
            int newDamage = currentDamage + event.getDamage();

            // If durability would be 1 or less (damage >= maxDurability - 1)
            if (newDamage >= maxDurability - 1) {
                // Upgrade the hoe!
                Bukkit.getScheduler().runTask(plugin, () -> {
                    upgradeHoe(item, event.getPlayer());
                    event.getPlayer().getInventory().setItemInMainHand(item);
                });
            }
        }
    }

    /**
     * Upgrade the Harvest Hoe to Hardworking Harvest Hoe.
     */
    private void upgradeHoe(ItemStack hoe, Player player) {
        if (hoe == null || !hoe.hasItemMeta()) {
            return;
        }

        ItemMeta meta = hoe.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Mark as upgraded
        pdc.set(PDCKeys.HARVEST_HOE_UPGRADED, PersistentDataType.BOOLEAN, true);

        // Set unbreakable
        meta.setUnbreakable(true);

        // Repair to full durability
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
        }

        // Apply rainbow name
        meta.displayName(createRainbowName("Hardworking Harvest Hoe"));

        hoe.setItemMeta(meta);

        // Update lore
        updateHoeLore(hoe);

        // Epic effects for the player
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

        // Particle explosion
        player.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                100,
                0.5, 0.5, 0.5,
                0.5
        );

        // Epic message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("LEGENDARY TRANSFORMATION", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" ✦", NamedTextColor.LIGHT_PURPLE)));
        player.sendMessage(Component.text("Your dedication has been rewarded!", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("The Harvest Hoe has become the ", NamedTextColor.GRAY)
                .append(createRainbowName("Hardworking Harvest Hoe"))
                .append(Component.text("!", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("• Base radius upgraded to 5x5", NamedTextColor.AQUA));
        player.sendMessage(Component.text("• Expanded radius now 7x7", NamedTextColor.AQUA));
        player.sendMessage(Component.text("• Now Unbreakable!", NamedTextColor.GREEN));
        player.sendMessage(Component.empty());
    }

    /**
     * Check if a Harvest Hoe has been upgraded.
     */
    private boolean isUpgradedHoe(ItemStack hoe) {
        if (hoe == null || !hoe.hasItemMeta()) {
            return false;
        }
        return hoe.getItemMeta().getPersistentDataContainer()
                .has(PDCKeys.HARVEST_HOE_UPGRADED, PersistentDataType.BOOLEAN);
    }

    /**
     * Create a rainbow-colored name component.
     */
    private Component createRainbowName(String text) {
        // Rainbow colors
        TextColor[] rainbowColors = {
                TextColor.color(255, 0, 0),     // Red
                TextColor.color(255, 127, 0),   // Orange
                TextColor.color(255, 255, 0),   // Yellow
                TextColor.color(0, 255, 0),     // Green
                TextColor.color(0, 127, 255),   // Cyan
                TextColor.color(0, 0, 255),     // Blue
                TextColor.color(139, 0, 255)    // Purple
        };

        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            TextColor color = rainbowColors[i % rainbowColors.length];
            builder.append(Component.text(c, color).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        }

        return builder.build();
    }

    // ==================== LOYALTY EASTER EGG ====================

    /**
     * "You CAN enchant THIS Hoe with Loyalty!"
     * Kill a Drowned with the Harvest Hoe, and if it drops a Trident,
     * the Hoe gets Loyalty III + a legendary announcement.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrownedKill(EntityDeathEvent event) {
        // Only Drowned mobs
        if (event.getEntityType() != EntityType.DROWNED) {
            return;
        }

        // Must have a player killer
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        // Must be holding Harvest Hoe in main hand
        ItemStack hoe = killer.getInventory().getItemInMainHand();
        if (!InventoryUtil.isCustomItem(hoe, CustomItemType.HARVEST_HOE)) {
            return;
        }

        // Check if the Drowned was holding a Trident (equipment drops don't appear in getDrops())
        EntityEquipment equipment = event.getEntity().getEquipment();
        if (equipment == null || equipment.getItemInMainHand().getType() != Material.TRIDENT) {
            return;
        }

        // Don't re-trigger if hoe already has Loyalty
        if (hoe.containsEnchantment(Enchantment.LOYALTY)) {
            return;
        }

        // Add Loyalty III to the Hoe (unsafe because Loyalty isn't normally valid on hoes)
        hoe.addUnsafeEnchantment(Enchantment.LOYALTY, 3);

        // Update in hand
        killer.getInventory().setItemInMainHand(hoe);

        // Epic effects
        killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        killer.playSound(killer.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.0f);
        killer.playSound(killer.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.5f, 1.5f);

        // Particle explosion
        killer.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                killer.getLocation().add(0, 1, 0),
                60,
                0.5, 0.5, 0.5,
                0.3
        );
        killer.getWorld().spawnParticle(
                Particle.ENCHANT,
                killer.getLocation().add(0, 1.5, 0),
                40,
                0.5, 0.5, 0.5,
                1.0
        );

        // Epic chat announcement
        killer.sendMessage(Component.empty());
        killer.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("You CAN enchant THIS Hoe with Loyalty!", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, true))
                .append(Component.text(" ✦", NamedTextColor.GOLD)));
        killer.sendMessage(Component.text("A drowned's tribute to your dedication.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        killer.sendMessage(Component.text("Loyalty III", NamedTextColor.AQUA)
                .append(Component.text(" has been bestowed upon your Harvest Hoe!", NamedTextColor.GRAY)));
        killer.sendMessage(Component.empty());
    }
}
