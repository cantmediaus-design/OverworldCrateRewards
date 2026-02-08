package com.overworldcraterewards.features.lumberjacksmark;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.config.ConfigManager;
import com.overworldcraterewards.data.PDCKeys;
import com.overworldcraterewards.economy.EconomyManager;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the Lumberjack's Mark accessory that grants bonus stripped logs
 * and flat economy bonus when breaking logs.
 *
 * Easter Egg: At 250,000 bonus logs, upgrades to "Woodland's Bounty" with:
 * - 25% bonus chance (up from 15%)
 * - $0.50 flat bonus (up from $0.25)
 * - 5% chance for apple drop from any tree
 */
public class LumberjacksMarkListener implements Listener {

    private final OverworldCrateRewardsPlugin plugin;
    private final ConfigManager config;
    private final EconomyManager economy;

    // Log → Stripped Log mapping (built safely to handle missing materials on older versions)
    private static final Map<Material, Material> LOG_TO_STRIPPED;

    static {
        Map<Material, Material> map = new HashMap<>();
        // Overworld logs (guaranteed in 1.21+)
        map.put(Material.OAK_LOG, Material.STRIPPED_OAK_LOG);
        map.put(Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG);
        map.put(Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG);
        map.put(Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG);
        map.put(Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG);
        map.put(Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG);
        map.put(Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG);
        map.put(Material.CHERRY_LOG, Material.STRIPPED_CHERRY_LOG);
        // Overworld wood (bark blocks)
        map.put(Material.OAK_WOOD, Material.STRIPPED_OAK_WOOD);
        map.put(Material.SPRUCE_WOOD, Material.STRIPPED_SPRUCE_WOOD);
        map.put(Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_WOOD);
        map.put(Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD);
        map.put(Material.ACACIA_WOOD, Material.STRIPPED_ACACIA_WOOD);
        map.put(Material.DARK_OAK_WOOD, Material.STRIPPED_DARK_OAK_WOOD);
        map.put(Material.MANGROVE_WOOD, Material.STRIPPED_MANGROVE_WOOD);
        map.put(Material.CHERRY_WOOD, Material.STRIPPED_CHERRY_WOOD);
        // Nether stems
        map.put(Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM);
        map.put(Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM);
        // Nether hyphae
        map.put(Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_HYPHAE);
        map.put(Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_HYPHAE);

        // Pale Oak — only available in 1.21.4+ (Garden Awakens update)
        try {
            map.put(Material.valueOf("PALE_OAK_LOG"), Material.valueOf("STRIPPED_PALE_OAK_LOG"));
            map.put(Material.valueOf("PALE_OAK_WOOD"), Material.valueOf("STRIPPED_PALE_OAK_WOOD"));
        } catch (IllegalArgumentException ignored) {
            // Server version doesn't have Pale Oak materials
        }

        LOG_TO_STRIPPED = Map.copyOf(map);
    }

    public LumberjacksMarkListener(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.economy = plugin.getEconomyManager();
        plugin.getLogger().info("Lumberjack's Mark listener loaded with " + LOG_TO_STRIPPED.size() + " log types.");
    }

    /**
     * Prevent eating the sweet berries when holding Lumberjack's Mark.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (InventoryUtil.isCustomItem(item, CustomItemType.LUMBERJACKS_MARK)) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle log break: bonus stripped log drops and flat economy bonus.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material blockType = event.getBlock().getType();

        // Check if it's a log type
        Material strippedVariant = LOG_TO_STRIPPED.get(blockType);
        if (strippedVariant == null) {
            return;
        }

        Player player = event.getPlayer();

        // Find Lumberjack's Mark in inventory
        ItemStack mark = InventoryUtil.findItemInInventory(player, CustomItemType.LUMBERJACKS_MARK);
        if (mark == null) {
            return;
        }

        boolean upgraded = isUpgradedMark(mark);

        // Roll for bonus stripped log
        double bonusChance = upgraded ? config.getLumberjacksMarkUpgradedBonusChance() : config.getLumberjacksMarkBonusChance();
        if (Math.random() >= bonusChance) {
            return; // No bonus this time
        }

        // Drop bonus stripped log
        event.getBlock().getWorld().dropItemNaturally(
                event.getBlock().getLocation(),
                new ItemStack(strippedVariant, 1)
        );

        // Deposit flat bonus
        double flatBonus = upgraded ? config.getLumberjacksMarkUpgradedFlatBonus() : config.getLumberjacksMarkFlatBonus();
        economy.deposit(player, flatBonus);

        // Upgraded: 5% apple bonus
        if (upgraded && Math.random() < config.getLumberjacksMarkAppleChance()) {
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation(),
                    new ItemStack(Material.APPLE, 1)
            );
        }

        // Update stats
        updateMarkStats(player, mark, flatBonus);

        // Action bar notification
        String strippedName = MessageUtil.formatMaterialName(strippedVariant.name());
        player.sendActionBar(Component.text()
                .append(Component.text("+" + MessageUtil.formatCurrency(flatBonus), NamedTextColor.GREEN))
                .append(Component.text(" (Bonus " + strippedName + ")", NamedTextColor.GRAY))
                .build());
    }

    private boolean isUpgradedMark(ItemStack mark) {
        if (mark == null || !mark.hasItemMeta()) {
            return false;
        }
        return mark.getItemMeta().getPersistentDataContainer()
                .has(PDCKeys.LUMBERJACKS_MARK_UPGRADED, PersistentDataType.BOOLEAN);
    }

    /**
     * Update mark stats in PDC and check for upgrade threshold.
     */
    private void updateMarkStats(Player player, ItemStack mark, double earned) {
        if (mark == null || !mark.hasItemMeta()) {
            return;
        }

        ItemMeta meta = mark.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long currentLogs = pdc.getOrDefault(PDCKeys.LUMBERJACKS_MARK_BONUS_LOGS, PersistentDataType.LONG, 0L);
        double currentEarned = pdc.getOrDefault(PDCKeys.LUMBERJACKS_MARK_EARNED, PersistentDataType.DOUBLE, 0.0);

        long newLogs = currentLogs + 1;
        double newEarned = currentEarned + earned;

        pdc.set(PDCKeys.LUMBERJACKS_MARK_BONUS_LOGS, PersistentDataType.LONG, newLogs);
        pdc.set(PDCKeys.LUMBERJACKS_MARK_EARNED, PersistentDataType.DOUBLE, newEarned);
        mark.setItemMeta(meta);

        // Check for upgrade
        if (!isUpgradedMark(mark) && newLogs >= config.getLumberjacksMarkUpgradeThreshold()) {
            upgradeMark(mark, player);
        }

        // Update lore
        updateMarkLore(mark);

        // Update item in inventory
        updateMarkInInventory(player, mark);
    }

    /**
     * Find and update the mark in the player's inventory.
     */
    private void updateMarkInInventory(Player player, ItemStack updatedMark) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (InventoryUtil.isCustomItem(item, CustomItemType.LUMBERJACKS_MARK)) {
                player.getInventory().setItem(i, updatedMark);
                return;
            }
        }

        if (InventoryUtil.isCustomItem(player.getInventory().getItemInOffHand(), CustomItemType.LUMBERJACKS_MARK)) {
            player.getInventory().setItemInOffHand(updatedMark);
        }
    }

    /**
     * Update the Lumberjack's Mark lore to show stats.
     */
    private void updateMarkLore(ItemStack mark) {
        if (mark == null || !mark.hasItemMeta()) {
            return;
        }

        ItemMeta meta = mark.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long bonusLogs = pdc.getOrDefault(PDCKeys.LUMBERJACKS_MARK_BONUS_LOGS, PersistentDataType.LONG, 0L);
        double earned = pdc.getOrDefault(PDCKeys.LUMBERJACKS_MARK_EARNED, PersistentDataType.DOUBLE, 0.0);
        boolean upgraded = pdc.has(PDCKeys.LUMBERJACKS_MARK_UPGRADED, PersistentDataType.BOOLEAN);

        List<Component> lore = new ArrayList<>();

        // Show upgraded status
        if (upgraded) {
            lore.add(Component.text("\u2726 WOODLAND EDITION \u2726", NamedTextColor.DARK_GREEN)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("25% chance | $0.50 | 5% apple", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // Stats - only show if tracking has started
        if (bonusLogs > 0) {
            lore.add(Component.text("Bonus Logs: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(bonusLogs), NamedTextColor.DARK_GREEN))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Total Earned: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatCurrency(earned), NamedTextColor.GOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // Description
        lore.add(Component.text("Keep in your inventory", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        if (upgraded) {
            lore.add(Component.text("~25% bonus stripped log on log break", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("+$0.50 per bonus log", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("~15% bonus stripped log on log break", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("+$0.25 per bonus log", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        mark.setItemMeta(meta);
    }

    /**
     * Upgrade the Lumberjack's Mark to Woodland's Bounty.
     */
    private void upgradeMark(ItemStack mark, Player player) {
        if (mark == null || !mark.hasItemMeta()) {
            return;
        }

        ItemMeta meta = mark.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        pdc.set(PDCKeys.LUMBERJACKS_MARK_UPGRADED, PersistentDataType.BOOLEAN, true);
        meta.displayName(createWoodlandGradientName("Woodland's Bounty"));

        mark.setItemMeta(meta);

        // Epic effects
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_CHERRY_LEAVES_PLACE, 1.0f, 0.8f);

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
                0
        );
        player.getWorld().spawnParticle(
                Particle.FALLING_SPORE_BLOSSOM,
                player.getLocation().add(0, 2, 0),
                30,
                1.0, 0.5, 1.0,
                0
        );

        // Epic message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("\u2726 ", NamedTextColor.DARK_GREEN)
                .append(Component.text("FOREST'S GRATITUDE!", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" \u2726", NamedTextColor.DARK_GREEN)));
        player.sendMessage(Component.text("The ancient woods have recognized your tireless work!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("The Lumberjack's Mark has become ", NamedTextColor.GRAY)
                .append(createWoodlandGradientName("Woodland's Bounty"))
                .append(Component.text("!", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("\u2022 25% bonus chance (up from 15%)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("\u2022 $0.50 per bonus log (up from $0.25)", NamedTextColor.DARK_GREEN));
        player.sendMessage(Component.text("\u2022 5% chance for apple drop from any tree", NamedTextColor.GREEN));
        player.sendMessage(Component.empty());
    }

    /**
     * Create a brown-to-green gradient name component.
     */
    private Component createWoodlandGradientName(String text) {
        TextColor[] woodlandColors = {
                TextColor.color(139, 90, 43),    // Saddle Brown
                TextColor.color(120, 110, 40),   // Brown-Green
                TextColor.color(85, 130, 35),    // Olive
                TextColor.color(50, 150, 30),    // Medium Green
                TextColor.color(34, 139, 34)     // Forest Green
        };

        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            TextColor color = woodlandColors[i % woodlandColors.length];
            builder.append(Component.text(c, color)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
        }

        return builder.build();
    }
}
