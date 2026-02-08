package com.overworldcraterewards.features.soulsiphon;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.config.ConfigManager;
import com.overworldcraterewards.data.PDCKeys;
import com.overworldcraterewards.economy.EconomyManager;
import com.overworldcraterewards.hooks.RoseStackerHook;
import com.overworldcraterewards.items.CustomItemType;
import com.overworldcraterewards.util.InventoryUtil;
import com.overworldcraterewards.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the Soul Siphon accessory that grants bonus money on manual mob kills.
 * Supports multi-kill aggregation to show combined kills within a short time window.
 * Supports RoseStacker for stacked mob kills.
 *
 * Easter Egg: At 6,666,666 kills, upgrades to "Soulbound Siphon" with:
 * - +300% bonus ($4 instead of $1)
 * - 10% chance for double souls
 * - 10% chance to restore 1 hunger + 1 saturation
 */
public class SoulSiphonListener implements Listener {

    private final OverworldCrateRewardsPlugin plugin;
    private final ConfigManager config;
    private final EconomyManager economy;

    // Multi-kill aggregation: tracks pending kills per player
    private final Map<UUID, PendingKillData> pendingKills = new ConcurrentHashMap<>();

    public SoulSiphonListener(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.economy = plugin.getEconomyManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Must have a player killer
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        // Don't give bonus for killing players
        if (entity instanceof Player) {
            return;
        }

        // Verify it was a manual kill
        EntityDamageEvent lastDamage = entity.getLastDamageCause();
        if (!isManualKill(lastDamage, killer)) {
            return;
        }

        // Find Soul Siphon in inventory
        ItemStack siphon = InventoryUtil.findItemInInventory(killer, CustomItemType.SOUL_SIPHON);
        if (siphon == null) {
            return;
        }

        // Process the kill (1 kill for regular EntityDeathEvent)
        processKill(killer, siphon, 1);
    }

    /**
     * Handle RoseStacker multi-kill events.
     * This is called when multiple entities in a stack are killed at once.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onStackedEntityDeath(dev.rosewood.rosestacker.event.EntityStackMultipleDeathEvent event) {
        if (!RoseStackerHook.isAvailable()) {
            return;
        }

        // Get the killer from the main entity that was killed
        LivingEntity mainEntity = event.getMainEntity();
        if (mainEntity == null) {
            return;
        }

        Player killer = mainEntity.getKiller();
        if (killer == null) {
            return;
        }

        // Find Soul Siphon in inventory
        ItemStack siphon = InventoryUtil.findItemInInventory(killer, CustomItemType.SOUL_SIPHON);
        if (siphon == null) {
            return;
        }

        // Get the number of entities killed in this stack event
        int killCount = event.getEntityKillCount();
        if (killCount <= 0) {
            return;
        }

        // Process the kills
        processKill(killer, siphon, killCount);
    }

    /**
     * Process kills: calculate bonus, apply upgraded effects, deposit money, update stats.
     */
    private void processKill(Player killer, ItemStack siphon, int killCount) {
        boolean upgraded = isUpgradedSiphon(siphon);

        // Calculate bonus per kill
        double bonusPerKill = upgraded ? config.getSoulSiphonUpgradedBonus() : config.getSoulSiphonBonus();
        double totalBonus = bonusPerKill * killCount;

        // Track if any special effects triggered
        boolean doubleSoulsTriggered = false;
        boolean feedTriggered = false;

        // Apply upgraded effects for each kill
        if (upgraded) {
            for (int i = 0; i < killCount; i++) {
                // Chance for double souls
                if (Math.random() < config.getSoulSiphonUpgradedDoubleSoulsChance()) {
                    totalBonus += bonusPerKill;
                    doubleSoulsTriggered = true;
                }

                // Chance to restore hunger/saturation
                if (Math.random() < config.getSoulSiphonUpgradedFeedChance()) {
                    feedTriggered = true;
                }
            }
        }

        // Apply feed effect if triggered
        if (feedTriggered) {
            int newFood = Math.min(killer.getFoodLevel() + 1, 20);
            float newSaturation = Math.min(killer.getSaturation() + 1.0f, newFood);
            killer.setFoodLevel(newFood);
            killer.setSaturation(newSaturation);

            // Soul particle at player
            killer.getWorld().spawnParticle(
                    Particle.SOUL,
                    killer.getLocation().add(0, 1, 0),
                    5,
                    0.3, 0.3, 0.3,
                    0.02
            );
        }

        // Deposit bonus
        economy.deposit(killer, totalBonus);

        // Update stats and check for upgrade
        updateSiphonStats(killer, siphon, killCount, totalBonus);

        // Aggregate for display
        aggregateKill(killer, killCount, totalBonus, doubleSoulsTriggered);
    }

    /**
     * Update siphon stats in PDC and check for upgrade threshold.
     */
    private void updateSiphonStats(Player player, ItemStack siphon, int kills, double earned) {
        if (siphon == null || !siphon.hasItemMeta()) {
            return;
        }

        ItemMeta meta = siphon.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Get current values
        long currentKills = pdc.getOrDefault(PDCKeys.SOUL_SIPHON_KILLS, PersistentDataType.LONG, 0L);
        double currentEarned = pdc.getOrDefault(PDCKeys.SOUL_SIPHON_EARNED, PersistentDataType.DOUBLE, 0.0);

        // Update values
        long newKills = currentKills + kills;
        double newEarned = currentEarned + earned;

        pdc.set(PDCKeys.SOUL_SIPHON_KILLS, PersistentDataType.LONG, newKills);
        pdc.set(PDCKeys.SOUL_SIPHON_EARNED, PersistentDataType.DOUBLE, newEarned);
        siphon.setItemMeta(meta);

        // Check for upgrade
        if (!isUpgradedSiphon(siphon) && newKills >= config.getSoulSiphonUpgradeThreshold()) {
            upgradeSiphon(siphon, player);
        }

        // Update lore
        updateSiphonLore(siphon);

        // Update item in inventory
        updateSiphonInInventory(player, siphon);
    }

    /**
     * Find and update the siphon in the player's inventory.
     */
    private void updateSiphonInInventory(Player player, ItemStack updatedSiphon) {
        // Check main inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (InventoryUtil.isCustomItem(item, CustomItemType.SOUL_SIPHON)) {
                player.getInventory().setItem(i, updatedSiphon);
                return;
            }
        }

        // Check off-hand
        if (InventoryUtil.isCustomItem(player.getInventory().getItemInOffHand(), CustomItemType.SOUL_SIPHON)) {
            player.getInventory().setItemInOffHand(updatedSiphon);
        }
    }

    /**
     * Upgrade the Soul Siphon to Soulbound Siphon.
     */
    private void upgradeSiphon(ItemStack siphon, Player player) {
        if (siphon == null || !siphon.hasItemMeta()) {
            return;
        }

        ItemMeta meta = siphon.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Mark as upgraded
        pdc.set(PDCKeys.SOUL_SIPHON_UPGRADED, PersistentDataType.BOOLEAN, true);

        // Apply soul gradient name
        meta.displayName(createSoulGradientName("Soulbound Siphon"));

        siphon.setItemMeta(meta);

        // Epic effects
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.8f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.7f);

        // Soul particle explosion
        player.getWorld().spawnParticle(
                Particle.SOUL,
                player.getLocation().add(0, 1, 0),
                100,
                1.0, 1.0, 1.0,
                0.05
        );
        player.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                player.getLocation().add(0, 1, 0),
                50,
                0.5, 0.5, 0.5,
                0.02
        );

        // Epic message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ ", NamedTextColor.DARK_PURPLE)
                .append(Component.text("ANCIENT POWER AWAKENED", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" ✦", NamedTextColor.DARK_PURPLE)));
        player.sendMessage(Component.text("The souls you've claimed have bound themselves to you!", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("The Soul Siphon has become the ", NamedTextColor.GRAY)
                .append(createSoulGradientName("Soulbound Siphon"))
                .append(Component.text("!", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("• +300% bonus per kill ($4.00)", NamedTextColor.RED));
        player.sendMessage(Component.text("• 10% chance for double souls", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("• 10% chance to feed on their souls", NamedTextColor.DARK_PURPLE));
        player.sendMessage(Component.empty());
    }

    /**
     * Update the Soul Siphon lore to show stats.
     */
    private void updateSiphonLore(ItemStack siphon) {
        if (siphon == null || !siphon.hasItemMeta()) {
            return;
        }

        ItemMeta meta = siphon.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long kills = pdc.getOrDefault(PDCKeys.SOUL_SIPHON_KILLS, PersistentDataType.LONG, 0L);
        double earned = pdc.getOrDefault(PDCKeys.SOUL_SIPHON_EARNED, PersistentDataType.DOUBLE, 0.0);
        boolean upgraded = pdc.has(PDCKeys.SOUL_SIPHON_UPGRADED, PersistentDataType.BOOLEAN);

        List<Component> lore = new ArrayList<>();

        // Show upgraded status
        if (upgraded) {
            lore.add(Component.text("✦ SOULBOUND EDITION ✦", NamedTextColor.DARK_PURPLE)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("+300% bonus | 10% double | 10% feed", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // Stats
        lore.add(Component.text("Souls Claimed: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.formatNumber(kills), NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Total Earned: ", NamedTextColor.GRAY)
                .append(Component.text(MessageUtil.formatCurrency(earned), NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false));

        // Description
        lore.add(Component.empty());
        lore.add(Component.text("Keep in your inventory", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        String bonusText = "+$" + String.format("%.2f", upgraded ? config.getSoulSiphonUpgradedBonus() : config.getSoulSiphonBonus()) + " per manual mob kill";
        lore.add(Component.text(bonusText, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        siphon.setItemMeta(meta);
    }

    /**
     * Check if a Soul Siphon has been upgraded.
     */
    private boolean isUpgradedSiphon(ItemStack siphon) {
        if (siphon == null || !siphon.hasItemMeta()) {
            return false;
        }
        return siphon.getItemMeta().getPersistentDataContainer()
                .has(PDCKeys.SOUL_SIPHON_UPGRADED, PersistentDataType.BOOLEAN);
    }

    /**
     * Create a dark soul gradient name component (dark red to dark purple).
     */
    private Component createSoulGradientName(String text) {
        TextColor[] soulColors = {
                TextColor.color(139, 0, 0),     // Dark Red
                TextColor.color(148, 0, 87),    // Dark Magenta
                TextColor.color(128, 0, 128),   // Purple
                TextColor.color(75, 0, 130),    // Indigo
                TextColor.color(48, 0, 48)      // Dark Purple
        };

        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            TextColor color = soulColors[i % soulColors.length];
            builder.append(Component.text(c, color)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
        }

        return builder.build();
    }

    /**
     * Aggregate kills for display purposes.
     * Multiple kills within the time window will be combined into one message.
     */
    private void aggregateKill(Player killer, int killCount, double bonus, boolean doubleSoulsTriggered) {
        UUID playerUUID = killer.getUniqueId();
        PendingKillData data = pendingKills.get(playerUUID);

        if (data == null) {
            // First kill in this window - create new data and schedule display
            data = new PendingKillData(killCount, bonus, doubleSoulsTriggered);
            pendingKills.put(playerUUID, data);

            // Schedule display after the aggregation window
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                displayAggregatedKills(killer, playerUUID);
            }, config.getSoulSiphonMultiKillWindowTicks());
        } else {
            // Add to existing pending kills
            data.addKills(killCount, bonus, doubleSoulsTriggered);
        }
    }

    /**
     * Display the aggregated kill message and clear pending data.
     */
    private void displayAggregatedKills(Player player, UUID playerUUID) {
        PendingKillData data = pendingKills.remove(playerUUID);
        if (data == null || !player.isOnline()) {
            return;
        }

        // Build message based on kill count
        net.kyori.adventure.text.TextComponent.Builder msgBuilder = Component.text();

        if (data.doubleSoulsTriggered) {
            msgBuilder.append(Component.text("+" + MessageUtil.formatCurrency(data.totalBonus), NamedTextColor.GOLD));
        } else {
            msgBuilder.append(Component.text("+" + MessageUtil.formatCurrency(data.totalBonus), NamedTextColor.LIGHT_PURPLE));
        }

        if (data.killCount == 1) {
            // Single kill - simple format
            if (data.doubleSoulsTriggered) {
                msgBuilder.append(Component.text(" (Soul Siphon - DOUBLE!)", NamedTextColor.GOLD));
            } else {
                msgBuilder.append(Component.text(" (Soul Siphon)", NamedTextColor.DARK_PURPLE));
            }
        } else {
            // Multi-kill - show count
            if (data.doubleSoulsTriggered) {
                msgBuilder.append(Component.text(" (Soul Siphon - " + data.killCount + " kills, DOUBLE!)", NamedTextColor.GOLD));
            } else {
                msgBuilder.append(Component.text(" (Soul Siphon - " + data.killCount + " kills)", NamedTextColor.DARK_PURPLE));
            }
        }

        player.sendActionBar(msgBuilder.build());
    }

    /**
     * Check if the damage was dealt manually by the expected killer.
     * Excludes passive damage sources like fall damage, suffocation, etc.
     */
    private boolean isManualKill(EntityDamageEvent event, Player expectedKiller) {
        if (event == null) {
            return false;
        }

        // Exclude non-manual damage causes
        switch (event.getCause()) {
            case FALL:
            case SUFFOCATION:
            case CRAMMING:
            case DROWNING:
            case STARVATION:
            case FIRE_TICK:
            case POISON:
            case WITHER:
            case MAGIC: // Potions thrown by others, etc.
            case THORNS:
            case LAVA:
            case HOT_FLOOR:
            case VOID:
            case LIGHTNING:
            case FREEZE:
            case WORLD_BORDER:
            case CONTACT: // Cactus, berry bush
            case DRAGON_BREATH:
            case FLY_INTO_WALL:
            case SONIC_BOOM:
                return false;
            default:
                break;
        }

        // Must be entity damage by the expected killer
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }

        Entity damager = byEntity.getDamager();

        // Direct hit by player
        if (damager.equals(expectedKiller)) {
            return true;
        }

        // Projectile shot by player
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                return shooter.equals(expectedKiller);
            }
        }

        // Note: Pets/wolves killing mobs don't count - the damager would be the wolf, not the player
        return false;
    }

    /**
     * Data class to track pending kills for multi-kill aggregation.
     */
    private static class PendingKillData {
        int killCount;
        double totalBonus;
        boolean doubleSoulsTriggered;

        PendingKillData(int initialKills, double initialBonus, boolean doubleSouls) {
            this.killCount = initialKills;
            this.totalBonus = initialBonus;
            this.doubleSoulsTriggered = doubleSouls;
        }

        void addKills(int kills, double bonus, boolean doubleSouls) {
            this.killCount += kills;
            this.totalBonus += bonus;
            if (doubleSouls) {
                this.doubleSoulsTriggered = true;
            }
        }
    }
}
