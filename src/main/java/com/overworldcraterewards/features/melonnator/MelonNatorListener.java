package com.overworldcraterewards.features.melonnator;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.config.ConfigManager;
import com.overworldcraterewards.data.PDCKeys;
import com.overworldcraterewards.items.CustomItemType;
import com.overworldcraterewards.util.InventoryUtil;
import com.overworldcraterewards.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles the Melon-nator tool that instantly breaks melons
 * with an evolving glistering melon slice drop mechanic.
 *
 * Growth Mechanic:
 * - Each melon mined increases a "growth chance" by 0.0001%
 * - When growth triggers: resets growth chance, permanently multiplies glistering chance by x1.0001
 * - Glistering chance caps at 10%
 * - At cap: unlocks 5% chance to spawn an experience bottle
 *
 * Easter Egg: First growth trigger upgrades to "Melon Overlord"
 * - Doubled growth increment speed (0.0002% per melon)
 */
public class MelonNatorListener implements Listener {

    private final OverworldCrateRewardsPlugin plugin;
    private final ConfigManager config;

    public MelonNatorListener(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    /**
     * Make melons break instantly when starting to mine with the Melon-nator.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!InventoryUtil.isCustomItem(item, CustomItemType.MELON_NATOR)) {
            return;
        }

        if (event.getBlock().getType() == Material.MELON) {
            event.setInstaBreak(true);
        }
    }

    /**
     * Handle melon break: growth mechanic, glistering drops, juicy effects, XP bottles.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack melonNator = player.getInventory().getItemInMainHand();

        if (!InventoryUtil.isCustomItem(melonNator, CustomItemType.MELON_NATOR)) {
            return;
        }

        if (event.getBlock().getType() != Material.MELON) {
            return;
        }

        ItemMeta meta = melonNator.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean upgraded = isUpgraded(melonNator);

        // Read current values
        long melonsMined = pdc.getOrDefault(PDCKeys.MELON_NATOR_MELONS, PersistentDataType.LONG, 0L);
        double growthChance = pdc.getOrDefault(PDCKeys.MELON_NATOR_GROWTH_CHANCE, PersistentDataType.DOUBLE, 0.0);
        double glisteringChance = pdc.getOrDefault(PDCKeys.MELON_NATOR_GLISTERING_CHANCE, PersistentDataType.DOUBLE,
                config.getMelonNatorBaseGlisteringChance());
        long growthLevel = pdc.getOrDefault(PDCKeys.MELON_NATOR_GROWTH_LEVEL, PersistentDataType.LONG, 0L);

        melonsMined++;

        // Increment growth chance
        double increment = config.getMelonNatorGrowthIncrement();
        if (upgraded) {
            increment *= config.getMelonNatorUpgradedGrowthSpeedMultiplier(); // Upgraded: multiplied growth speed
        }
        growthChance += increment;

        // Roll for growth trigger
        if (Math.random() < growthChance) {
            growthChance = 0.0; // Reset growth chance
            growthLevel++;

            // Multiply glistering chance (cap it)
            double cap = config.getMelonNatorGlisteringCap();
            glisteringChance = Math.min(cap, glisteringChance * config.getMelonNatorGrowthMultiplier());

            // Growth trigger flair: green composter particles + crop plant sound
            player.getWorld().spawnParticle(
                    Particle.COMPOSTER,
                    event.getBlock().getLocation().add(0.5, 0.8, 0.5),
                    20,
                    0.3, 0.3, 0.3,
                    0.05
            );
            player.playSound(event.getBlock().getLocation(), Sound.ITEM_CROP_PLANT, 1.0f, 1.2f);

            player.sendActionBar(Component.text()
                    .append(Component.text("✦ Growth! ", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true))
                    .append(Component.text("Glistering Chance: " + formatPercent(glisteringChance), NamedTextColor.YELLOW))
                    .build());

            // Check for first growth trigger = upgrade
            if (!upgraded && growthLevel == 1) {
                // Save current state first
                pdc.set(PDCKeys.MELON_NATOR_MELONS, PersistentDataType.LONG, melonsMined);
                pdc.set(PDCKeys.MELON_NATOR_GROWTH_CHANCE, PersistentDataType.DOUBLE, growthChance);
                pdc.set(PDCKeys.MELON_NATOR_GLISTERING_CHANCE, PersistentDataType.DOUBLE, glisteringChance);
                pdc.set(PDCKeys.MELON_NATOR_GROWTH_LEVEL, PersistentDataType.LONG, growthLevel);
                melonNator.setItemMeta(meta);

                upgradeMelonNator(melonNator, player);
                updateLore(melonNator);
                player.getInventory().setItemInMainHand(melonNator);
                return;
            }
        }

        // Roll for glistering melon slice drop
        if (Math.random() < glisteringChance) {
            int count = ThreadLocalRandom.current().nextInt(
                    config.getMelonNatorMinGlistering(),
                    config.getMelonNatorMaxGlistering() + 1
            );
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation(),
                    new ItemStack(Material.GLISTERING_MELON_SLICE, count)
            );

            player.sendActionBar(Component.text()
                    .append(Component.text("+" + count + " Glistering Melon Slice", NamedTextColor.GOLD))
                    .append(Component.text(" (Bonus!)", NamedTextColor.YELLOW))
                    .build());

            // Shimmer particles for glistering drop
            player.getWorld().spawnParticle(
                    Particle.ENCHANT,
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    15,
                    0.3, 0.3, 0.3,
                    0.5
            );
            player.playSound(event.getBlock().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
        }

        // XP bottle drop (only when glistering chance is at cap)
        if (glisteringChance >= config.getMelonNatorGlisteringCap()
                && Math.random() < config.getMelonNatorXpBottleChance()) {
            ThrownExpBottle bottle = event.getBlock().getWorld().spawn(
                    event.getBlock().getLocation().add(0.5, 1.0, 0.5),
                    ThrownExpBottle.class
            );
            bottle.setVelocity(new Vector(0, 0.3, 0)); // Slight upward arc
            player.playSound(event.getBlock().getLocation(), Sound.ENTITY_EXPERIENCE_BOTTLE_THROW, 0.8f, 1.0f);
        }

        // "Juicy" cosmetic proc (2% chance)
        if (Math.random() < config.getMelonNatorJuicyChance()) {
            player.getWorld().spawnParticle(
                    Particle.ITEM,
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    12,
                    0.3, 0.3, 0.3,
                    0.05,
                    new ItemStack(Material.MELON_SLICE)
            );
            player.playSound(event.getBlock().getLocation(), Sound.ENTITY_SLIME_SQUISH, 0.6f, 1.2f);
        }

        // Save all stats
        pdc.set(PDCKeys.MELON_NATOR_MELONS, PersistentDataType.LONG, melonsMined);
        pdc.set(PDCKeys.MELON_NATOR_GROWTH_CHANCE, PersistentDataType.DOUBLE, growthChance);
        pdc.set(PDCKeys.MELON_NATOR_GLISTERING_CHANCE, PersistentDataType.DOUBLE, glisteringChance);
        pdc.set(PDCKeys.MELON_NATOR_GROWTH_LEVEL, PersistentDataType.LONG, growthLevel);
        melonNator.setItemMeta(meta);

        // Update lore and hand
        updateLore(melonNator);
        player.getInventory().setItemInMainHand(melonNator);
    }

    /**
     * Check if a Melon-nator has been upgraded.
     */
    private boolean isUpgraded(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(PDCKeys.MELON_NATOR_UPGRADED, PersistentDataType.BOOLEAN);
    }

    /**
     * Update the Melon-nator lore to show stats.
     */
    private void updateLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long melons = pdc.getOrDefault(PDCKeys.MELON_NATOR_MELONS, PersistentDataType.LONG, 0L);
        long growthLevel = pdc.getOrDefault(PDCKeys.MELON_NATOR_GROWTH_LEVEL, PersistentDataType.LONG, 0L);
        double glisteringChance = pdc.getOrDefault(PDCKeys.MELON_NATOR_GLISTERING_CHANCE, PersistentDataType.DOUBLE,
                config.getMelonNatorBaseGlisteringChance());
        boolean upgraded = pdc.has(PDCKeys.MELON_NATOR_UPGRADED, PersistentDataType.BOOLEAN);

        List<Component> lore = new ArrayList<>();

        // Show upgraded status
        if (upgraded) {
            lore.add(Component.text("✦ MELON OVERLORD ✦", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("2x growth speed", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // Stats
        if (melons > 0) {
            lore.add(Component.text("Melons Mined: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(melons), NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (growthLevel > 0) {
            lore.add(Component.text("Growth Level: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(growthLevel), NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Glistering Chance: ", NamedTextColor.GRAY)
                .append(Component.text(formatPercent(glisteringChance), NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false));

        // Show XP bottle unlock if at cap
        if (glisteringChance >= config.getMelonNatorGlisteringCap()) {
            lore.add(Component.text("✦ XP Bottle drops unlocked!", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());

        // Description
        lore.add(Component.text("Instantly breaks melons", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Evolving glistering melon chance", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Upgrade the Melon-nator to "Melon Overlord".
     */
    private void upgradeMelonNator(ItemStack item, Player player) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Mark as upgraded
        pdc.set(PDCKeys.MELON_NATOR_UPGRADED, PersistentDataType.BOOLEAN, true);

        // Apply watermelon gradient name
        meta.displayName(createMelonGradientName("Melon Overlord"));

        item.setItemMeta(meta);

        // Epic effects
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);

        // Particle explosion
        player.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                75,
                0.5, 0.5, 0.5,
                0.3
        );
        player.getWorld().spawnParticle(
                Particle.COMPOSTER,
                player.getLocation().add(0, 1, 0),
                40,
                0.5, 0.5, 0.5,
                0.1
        );

        // Epic message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ ", NamedTextColor.GREEN)
                .append(Component.text("THE SEED HAS SPROUTED!", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" ✦", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Your first growth trigger has awakened something within!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("The Melon-nator has become the ", NamedTextColor.GRAY)
                .append(createMelonGradientName("Melon Overlord"))
                .append(Component.text("!", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("• 2x growth speed (0.0002% per melon)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("• Watch your glistering chance evolve...", NamedTextColor.YELLOW));
        player.sendMessage(Component.empty());
    }

    /**
     * Create a red-to-pink-to-green watermelon gradient name.
     */
    private Component createMelonGradientName(String text) {
        TextColor[] melonColors = {
                TextColor.color(220, 50, 50),   // Red (flesh)
                TextColor.color(255, 100, 100),  // Light red
                TextColor.color(255, 150, 180),  // Pink
                TextColor.color(150, 220, 100),  // Light green
                TextColor.color(50, 180, 50)     // Green (rind)
        };

        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            TextColor color = melonColors[i % melonColors.length];
            builder.append(Component.text(c, color)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
        }

        return builder.build();
    }

    /**
     * Format a double as a percentage string with appropriate precision.
     */
    private String formatPercent(double value) {
        if (value < 0.001) {
            return String.format("%.4f%%", value * 100);
        } else if (value < 0.01) {
            return String.format("%.3f%%", value * 100);
        } else if (value < 0.1) {
            return String.format("%.2f%%", value * 100);
        } else {
            return String.format("%.1f%%", value * 100);
        }
    }

    // === Enchantment Prevention ===

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (InventoryUtil.isCustomItem(event.getItem(), CustomItemType.MELON_NATOR)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchant(EnchantItemEvent event) {
        if (InventoryUtil.isCustomItem(event.getItem(), CustomItemType.MELON_NATOR)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack firstItem = event.getInventory().getFirstItem();
        ItemStack secondItem = event.getInventory().getSecondItem();

        if (InventoryUtil.isCustomItem(firstItem, CustomItemType.MELON_NATOR) ||
            InventoryUtil.isCustomItem(secondItem, CustomItemType.MELON_NATOR)) {
            event.setResult(null);
        }
    }
}
