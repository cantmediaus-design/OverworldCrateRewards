package com.overworldcraterewards.features.jackohammer;

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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles the Jack'o'Hammer tool that instantly breaks pumpkins
 * with a chance for bonus pumpkin seed drops.
 *
 * Easter Egg: At 500,000 pumpkins harvested, upgrades to "Jack'o'Smasher" with:
 * - 50% bonus seed chance (up from 25%)
 * - 2-6 seed range (up from 1-4)
 * - 5% chance for carved pumpkin bonus drop
 */
public class JackoHammerListener implements Listener {

    private final OverworldCrateRewardsPlugin plugin;
    private final ConfigManager config;

    public JackoHammerListener(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    /**
     * Make pumpkins break instantly when starting to mine with the Jack'o'Hammer.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!InventoryUtil.isCustomItem(item, CustomItemType.JACKO_HAMMER)) {
            return;
        }

        if (event.getBlock().getType() == Material.PUMPKIN) {
            event.setInstaBreak(true);
        }
    }

    /**
     * Handle pumpkin break: bonus seed drops and stat tracking.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack hammer = player.getInventory().getItemInMainHand();

        if (!InventoryUtil.isCustomItem(hammer, CustomItemType.JACKO_HAMMER)) {
            return;
        }

        if (event.getBlock().getType() != Material.PUMPKIN) {
            return;
        }

        boolean upgraded = isUpgradedHammer(hammer);

        // Determine seed drop parameters
        double seedChance = upgraded ? config.getJackoHammerUpgradedSeedChance() : config.getJackoHammerBonusSeedChance();
        int minSeeds = upgraded ? config.getJackoHammerUpgradedMinSeeds() : config.getJackoHammerMinSeeds();
        int maxSeeds = upgraded ? config.getJackoHammerUpgradedMaxSeeds() : config.getJackoHammerMaxSeeds();

        // Roll for bonus seed drop
        if (Math.random() < seedChance) {
            int seedCount = ThreadLocalRandom.current().nextInt(minSeeds, maxSeeds + 1);
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation(),
                    new ItemStack(Material.PUMPKIN_SEEDS, seedCount)
            );

            // Action bar notification
            player.sendActionBar(Component.text()
                    .append(Component.text("+" + seedCount + " Pumpkin Seeds", NamedTextColor.GOLD))
                    .append(Component.text(" (Bonus!)", NamedTextColor.YELLOW))
                    .build());

            // Visual flair: pumpkin smash particles + crunch sound
            player.playSound(event.getBlock().getLocation(), Sound.BLOCK_PUMPKIN_CARVE, 1.0f, 0.8f);
            player.playSound(event.getBlock().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
            player.getWorld().spawnParticle(
                    Particle.FLAME,
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    15,
                    0.3, 0.3, 0.3,
                    0.02
            );
        }

        // Upgraded: 5% chance for carved pumpkin bonus
        if (upgraded && Math.random() < config.getJackoHammerCarvedPumpkinChance()) {
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation(),
                    new ItemStack(Material.CARVED_PUMPKIN, 1)
            );

            player.sendActionBar(Component.text()
                    .append(Component.text("+1 Carved Pumpkin", NamedTextColor.GOLD))
                    .append(Component.text(" (Rare!)", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.BOLD, true))
                    .build());
        }

        // Update stats
        updateHammerStats(player, hammer);
    }

    /**
     * Update hammer stats in PDC and check for upgrade threshold.
     */
    private void updateHammerStats(Player player, ItemStack hammer) {
        if (hammer == null || !hammer.hasItemMeta()) {
            return;
        }

        ItemMeta meta = hammer.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long currentPumpkins = pdc.getOrDefault(PDCKeys.JACKO_HAMMER_PUMPKINS, PersistentDataType.LONG, 0L);
        long newPumpkins = currentPumpkins + 1;

        pdc.set(PDCKeys.JACKO_HAMMER_PUMPKINS, PersistentDataType.LONG, newPumpkins);
        hammer.setItemMeta(meta);

        // Check for upgrade
        if (!isUpgradedHammer(hammer) && newPumpkins >= config.getJackoHammerUpgradeThreshold()) {
            upgradeHammer(hammer, player);
        }

        // Update lore
        updateHammerLore(hammer);

        // Update item in hand
        player.getInventory().setItemInMainHand(hammer);
    }

    /**
     * Check if a Jack'o'Hammer has been upgraded.
     */
    private boolean isUpgradedHammer(ItemStack hammer) {
        if (hammer == null || !hammer.hasItemMeta()) {
            return false;
        }
        return hammer.getItemMeta().getPersistentDataContainer()
                .has(PDCKeys.JACKO_HAMMER_UPGRADED, PersistentDataType.BOOLEAN);
    }

    /**
     * Update the Jack'o'Hammer lore to show stats.
     */
    private void updateHammerLore(ItemStack hammer) {
        if (hammer == null || !hammer.hasItemMeta()) {
            return;
        }

        ItemMeta meta = hammer.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long pumpkins = pdc.getOrDefault(PDCKeys.JACKO_HAMMER_PUMPKINS, PersistentDataType.LONG, 0L);
        boolean upgraded = pdc.has(PDCKeys.JACKO_HAMMER_UPGRADED, PersistentDataType.BOOLEAN);

        List<Component> lore = new ArrayList<>();

        // Show upgraded status
        if (upgraded) {
            lore.add(Component.text("\u2726 SMASHER EDITION \u2726", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("50% seeds | 2-6 range | 5% carved", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // Stats - only show if tracking has started
        if (pumpkins > 0) {
            lore.add(Component.text("Pumpkins Smashed: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(pumpkins), NamedTextColor.GOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // Description
        lore.add(Component.text("Instantly breaks pumpkins", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        if (upgraded) {
            lore.add(Component.text("~50% bonus pumpkin seed drop", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("~25% bonus pumpkin seed drop", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        hammer.setItemMeta(meta);
    }

    /**
     * Upgrade the Jack'o'Hammer to Jack'o'Smasher.
     */
    private void upgradeHammer(ItemStack hammer, Player player) {
        if (hammer == null || !hammer.hasItemMeta()) {
            return;
        }

        ItemMeta meta = hammer.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Mark as upgraded
        pdc.set(PDCKeys.JACKO_HAMMER_UPGRADED, PersistentDataType.BOOLEAN, true);

        // Apply pumpkin gradient name
        meta.displayName(createPumpkinGradientName("Jack'o'Smasher"));

        hammer.setItemMeta(meta);

        // Epic effects
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 0.8f);

        // Particle explosion
        player.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                75,
                0.5, 0.5, 0.5,
                0.3
        );
        player.getWorld().spawnParticle(
                Particle.FLAME,
                player.getLocation().add(0, 1, 0),
                40,
                0.5, 0.5, 0.5,
                0.05
        );

        // Epic message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("\u2726 ", NamedTextColor.GOLD)
                .append(Component.text("PUMPKIN ANNIHILATOR!", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" \u2726", NamedTextColor.GOLD)));
        player.sendMessage(Component.text("Your relentless pumpkin smashing has forged something greater!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("The Jack'o'Hammer has become the ", NamedTextColor.GRAY)
                .append(createPumpkinGradientName("Jack'o'Smasher"))
                .append(Component.text("!", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("\u2022 50% bonus seed chance (up from 25%)", NamedTextColor.GOLD));
        player.sendMessage(Component.text("\u2022 2-6 seed range (up from 1-4)", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("\u2022 5% chance for bonus carved pumpkin", NamedTextColor.GOLD));
        player.sendMessage(Component.empty());
    }

    /**
     * Create an orange-to-yellow gradient name component.
     */
    private Component createPumpkinGradientName(String text) {
        TextColor[] pumpkinColors = {
                TextColor.color(255, 140, 0),   // Dark Orange
                TextColor.color(255, 165, 0),   // Orange
                TextColor.color(255, 185, 50),  // Light Orange
                TextColor.color(255, 215, 0),   // Gold
                TextColor.color(255, 255, 0)    // Yellow
        };

        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            TextColor color = pumpkinColors[i % pumpkinColors.length];
            builder.append(Component.text(c, color)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
        }

        return builder.build();
    }

    // === Enchantment Prevention ===

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (InventoryUtil.isCustomItem(event.getItem(), CustomItemType.JACKO_HAMMER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchant(EnchantItemEvent event) {
        if (InventoryUtil.isCustomItem(event.getItem(), CustomItemType.JACKO_HAMMER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack firstItem = event.getInventory().getFirstItem();
        ItemStack secondItem = event.getInventory().getSecondItem();

        if (InventoryUtil.isCustomItem(firstItem, CustomItemType.JACKO_HAMMER) ||
            InventoryUtil.isCustomItem(secondItem, CustomItemType.JACKO_HAMMER)) {
            event.setResult(null);
        }
    }
}
