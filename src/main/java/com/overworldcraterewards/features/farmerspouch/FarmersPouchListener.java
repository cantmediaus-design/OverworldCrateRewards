package com.overworldcraterewards.features.farmerspouch;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.config.ConfigManager;
import com.overworldcraterewards.data.PDCKeys;
import com.overworldcraterewards.economy.EconomyManager;
import com.overworldcraterewards.hooks.EconomyShopGUIHook;
import com.overworldcraterewards.hooks.RoseStackerHook;
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
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the Farmer's Pouch accessory that auto-sells crops on pickup.
 * Items are sold instantly and never enter the player's inventory.
 * Can be toggled on/off by shift+right-clicking while holding it.
 *
 * Easter Egg: At $1,000,000,000 earned, upgrades to "Bountiful Pouch" with:
 * - +15% sell price on all crops
 * - 3% chance for lucky 2x payment
 */
public class FarmersPouchListener implements Listener {

    private final OverworldCrateRewardsPlugin plugin;
    private final ConfigManager config;
    private final EconomyManager economy;

    /**
     * Result of selling a crop through the Farmer's Pouch.
     */
    public record SellResult(double price, boolean luckyTriggered) {}

    public FarmersPouchListener(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.economy = plugin.getEconomyManager();
    }

    /**
     * Handle right-click interactions on the Farmer's Pouch.
     * Blocks vanilla bundle UI and handles shift+right-click to toggle on/off.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggle(PlayerInteractEvent event) {
        // Only handle right-click actions
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only main hand
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if holding Farmer's Pouch
        if (!InventoryUtil.isCustomItem(item, CustomItemType.FARMERS_POUCH)) {
            return;
        }

        // ALWAYS cancel to prevent vanilla bundle UI from opening
        event.setCancelled(true);

        // Only toggle if sneaking (shift)
        if (!player.isSneaking()) {
            return;
        }

        // Toggle the disabled state
        boolean isNowDisabled = toggleDisabled(item);

        // Update lore to show status
        updatePouchLore(item);

        // Update item in hand
        player.getInventory().setItemInMainHand(item);

        // Notify player
        if (isNowDisabled) {
            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(config.getMessagePrefix()))
                    .append(Component.text("Farmer's Pouch ", NamedTextColor.RED))
                    .append(Component.text("DISABLED", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(" - Crops will go to inventory", NamedTextColor.GRAY))
                    .build());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        } else {
            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(config.getMessagePrefix()))
                    .append(Component.text("Farmer's Pouch ", NamedTextColor.GREEN))
                    .append(Component.text("ENABLED", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(" - Crops will auto-sell", NamedTextColor.GRAY))
                    .build());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
        }
    }

    /**
     * Toggle the disabled state of a Farmer's Pouch.
     * @return true if now disabled, false if now enabled
     */
    private boolean toggleDisabled(ItemStack pouch) {
        ItemMeta meta = pouch.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean wasDisabled = pdc.getOrDefault(PDCKeys.FARMERS_POUCH_DISABLED, PersistentDataType.BOOLEAN, false);
        boolean isNowDisabled = !wasDisabled;

        if (isNowDisabled) {
            pdc.set(PDCKeys.FARMERS_POUCH_DISABLED, PersistentDataType.BOOLEAN, true);
        } else {
            pdc.remove(PDCKeys.FARMERS_POUCH_DISABLED);
        }

        pouch.setItemMeta(meta);
        return isNowDisabled;
    }

    /**
     * Check if a Farmer's Pouch is disabled.
     */
    private boolean isDisabled(ItemStack pouch) {
        if (pouch == null || !pouch.hasItemMeta()) {
            return false;
        }
        return pouch.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.FARMERS_POUCH_DISABLED, PersistentDataType.BOOLEAN, false);
    }

    /**
     * Check if a Farmer's Pouch has been upgraded.
     */
    private boolean isUpgradedPouch(ItemStack pouch) {
        if (pouch == null || !pouch.hasItemMeta()) {
            return false;
        }
        return pouch.getItemMeta().getPersistentDataContainer()
                .has(PDCKeys.FARMERS_POUCH_UPGRADED, PersistentDataType.BOOLEAN);
    }

    /**
     * Update the Farmer's Pouch lore to show enabled/disabled status and stats.
     */
    private void updatePouchLore(ItemStack pouch) {
        if (pouch == null || !pouch.hasItemMeta()) {
            return;
        }

        ItemMeta meta = pouch.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean disabled = pdc.getOrDefault(PDCKeys.FARMERS_POUCH_DISABLED, PersistentDataType.BOOLEAN, false);
        boolean upgraded = pdc.has(PDCKeys.FARMERS_POUCH_UPGRADED, PersistentDataType.BOOLEAN);
        double earned = pdc.getOrDefault(PDCKeys.FARMERS_POUCH_EARNED, PersistentDataType.DOUBLE, 0.0);

        List<Component> lore = new ArrayList<>();

        // Show upgraded status
        if (upgraded) {
            lore.add(Component.text("✦ BOUNTIFUL EDITION ✦", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("+15% sell price | 3% lucky 2x", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // Status line
        if (disabled) {
            lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                    .append(Component.text("DISABLED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true))
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                    .append(Component.text("ENABLED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true))
                    .decoration(TextDecoration.ITALIC, false));
        }

        // Stats - only show if tracking has started
        if (earned > 0) {
            lore.add(Component.empty());
            lore.add(Component.text("Total Earned: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatCurrency(earned), NamedTextColor.GOLD))
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());

        // Description
        if (upgraded) {
            lore.add(Component.text("Auto-sells crops on pickup (+15%)", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Auto-sells crops on pickup", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Shift+Right-Click to toggle", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);

        // Hide vanilla bundle tooltip (must be re-applied when updating lore)
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        pouch.setItemMeta(meta);
    }

    /**
     * Upgrade the Farmer's Pouch to Bountiful Pouch.
     */
    private void upgradePouch(ItemStack pouch, Player player) {
        if (pouch == null || !pouch.hasItemMeta()) {
            return;
        }

        ItemMeta meta = pouch.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Mark as upgraded
        pdc.set(PDCKeys.FARMERS_POUCH_UPGRADED, PersistentDataType.BOOLEAN, true);

        // Apply bountiful gradient name
        meta.displayName(createBountifulGradientName("Bountiful Pouch"));

        pouch.setItemMeta(meta);

        // Epic effects
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_COMPOSTER_READY, 1.0f, 1.0f);

        // Particle explosion
        player.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                75,
                0.5, 0.5, 0.5,
                0.3
        );
        player.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0),
                50,
                1.0, 0.5, 1.0,
                0
        );
        player.getWorld().spawnParticle(
                Particle.COMPOSTER,
                player.getLocation().add(0, 1, 0),
                30,
                0.5, 0.5, 0.5,
                0
        );

        // Epic message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("HARVEST FESTIVAL!", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" ✦", NamedTextColor.GOLD)));
        player.sendMessage(Component.text("Your dedication to the fields has been rewarded!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("The Farmer's Pouch has become the ", NamedTextColor.GRAY)
                .append(createBountifulGradientName("Bountiful Pouch"))
                .append(Component.text("!", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("• +15% sell price on all crops", NamedTextColor.GOLD));
        player.sendMessage(Component.text("• 3% chance for lucky double payment", NamedTextColor.YELLOW));
        player.sendMessage(Component.empty());
    }

    /**
     * Create a gold-green gradient name component for bountiful theme.
     */
    private Component createBountifulGradientName(String text) {
        TextColor[] bountifulColors = {
                TextColor.color(255, 215, 0),   // Gold
                TextColor.color(218, 235, 52),  // Yellow-Green
                TextColor.color(173, 255, 47),  // Green Yellow
                TextColor.color(50, 205, 50),   // Lime Green
                TextColor.color(34, 139, 34)    // Forest Green
        };

        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            TextColor color = bountifulColors[i % bountifulColors.length];
            builder.append(Component.text(c, color)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
        }

        return builder.build();
    }

    /**
     * Update pouch stats and check for upgrade.
     */
    private void updatePouchStats(Player player, ItemStack pouch, double earned) {
        if (pouch == null || !pouch.hasItemMeta()) {
            return;
        }

        ItemMeta meta = pouch.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Get current value
        double currentEarned = pdc.getOrDefault(PDCKeys.FARMERS_POUCH_EARNED, PersistentDataType.DOUBLE, 0.0);
        double newEarned = currentEarned + earned;

        // Update value
        pdc.set(PDCKeys.FARMERS_POUCH_EARNED, PersistentDataType.DOUBLE, newEarned);
        pouch.setItemMeta(meta);

        // Check for upgrade
        if (!isUpgradedPouch(pouch) && newEarned >= config.getFarmersPouchUpgradeThreshold()) {
            upgradePouch(pouch, player);
        }

        // Update lore
        updatePouchLore(pouch);

        // Update item in inventory
        updatePouchInInventory(player, pouch);
    }

    /**
     * Find and update the pouch in the player's inventory.
     */
    private void updatePouchInInventory(Player player, ItemStack updatedPouch) {
        // Check main inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (InventoryUtil.isCustomItem(item, CustomItemType.FARMERS_POUCH)) {
                player.getInventory().setItem(i, updatedPouch);
                return;
            }
        }

        // Check off-hand
        if (InventoryUtil.isCustomItem(player.getInventory().getItemInOffHand(), CustomItemType.FARMERS_POUCH)) {
            player.getInventory().setItemInOffHand(updatedPouch);
        }
    }

    /**
     * Sell a crop using the Farmer's Pouch pricing logic.
     * Applies upgrade bonuses if the pouch is upgraded.
     * Does NOT update stats - caller must call updatePouchStatsExternal separately.
     *
     * @param player The player selling the crop
     * @param material The crop material
     * @param amount The amount being sold
     * @param pouch The Farmer's Pouch item (must not be null)
     * @return SellResult with final price and lucky status, or null if not sellable
     */
    public SellResult sellCrop(Player player, Material material, int amount, ItemStack pouch) {
        // Get base price from EconomyShopGUI first
        Double price = null;
        if (EconomyShopGUIHook.isAvailable() && EconomyShopGUIHook.isSupportedCrop(material)) {
            price = EconomyShopGUIHook.getSellPrice(player, material, amount);
        }

        // Fall back to config price
        if (price == null) {
            Double configPrice = config.getCropPrice(material);
            if (configPrice == null) {
                return null; // Not a sellable crop
            }
            price = configPrice * amount;
        }

        // Apply upgrade bonuses
        boolean upgraded = isUpgradedPouch(pouch);
        double priceMultiplier = 1.0;
        boolean luckyTriggered = false;

        if (upgraded) {
            priceMultiplier = config.getFarmersPouchUpgradedSellMultiplier();

            // Lucky harvest
            if (Math.random() < config.getFarmersPouchLuckyChance()) {
                priceMultiplier *= 2;
                luckyTriggered = true;
            }
        }

        double finalPrice = price * priceMultiplier;

        // Deposit money
        economy.deposit(player, finalPrice);

        return new SellResult(finalPrice, luckyTriggered);
    }

    /**
     * Update pouch stats from external callers (e.g., HarvestHoeListener).
     * This allows other features to contribute to pouch earnings tracking.
     */
    public void updatePouchStatsExternal(Player player, ItemStack pouch, double earned) {
        updatePouchStats(player, pouch, earned);
    }

    /**
     * Check if a Farmer's Pouch is disabled (for external callers).
     */
    public boolean isPouchDisabled(ItemStack pouch) {
        return isDisabled(pouch);
    }

    /**
     * Check if a Farmer's Pouch is upgraded (for external callers).
     */
    public boolean isPouchUpgraded(ItemStack pouch) {
        return isUpgradedPouch(pouch);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        // Only handle player pickups
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Item itemEntity = event.getItem();
        ItemStack pickedUp = itemEntity.getItemStack();
        Material material = pickedUp.getType();
        int amount = RoseStackerHook.getStackedItemAmount(itemEntity);

        // Try to get price from EconomyShopGUI first (for crops only)
        Double price = null;
        if (EconomyShopGUIHook.isAvailable() && EconomyShopGUIHook.isSupportedCrop(material)) {
            price = EconomyShopGUIHook.getSellPrice(player, material, amount);
        }

        // Fall back to config price if shop price not available
        if (price == null) {
            Double configPrice = config.getCropPrice(material);
            if (configPrice == null) {
                return; // Not a sellable crop
            }
            price = configPrice * amount;
        }

        // Find the Farmer's Pouch in inventory and check if it's disabled
        ItemStack pouch = InventoryUtil.findItemInInventory(player, CustomItemType.FARMERS_POUCH);
        if (pouch == null) {
            return;
        }

        // Check if the pouch is disabled
        if (isDisabled(pouch)) {
            return; // Pouch is disabled, let item go to inventory normally
        }

        // Check if upgraded and apply bonuses
        boolean upgraded = isUpgradedPouch(pouch);
        double priceMultiplier = 1.0;
        boolean luckyTriggered = false;

        if (upgraded) {
            priceMultiplier = config.getFarmersPouchUpgradedSellMultiplier();

            // Lucky harvest
            if (Math.random() < config.getFarmersPouchLuckyChance()) {
                priceMultiplier *= 2;
                luckyTriggered = true;
            }
        }

        double finalPrice = price * priceMultiplier;

        // Cancel the pickup - item never enters inventory
        event.setCancelled(true);

        // Deposit money
        economy.deposit(player, finalPrice);

        // Remove item from world
        itemEntity.remove();

        // Update stats
        updatePouchStats(player, pouch, finalPrice);

        // Notify player with action bar
        String materialName = MessageUtil.formatMaterialName(material.name());

        if (luckyTriggered) {
            // Lucky harvest message with special formatting
            player.sendActionBar(Component.text()
                    .append(Component.text("+" + MessageUtil.formatCurrency(finalPrice), NamedTextColor.GOLD))
                    .append(Component.text(" (" + amount + "x " + materialName + ") ", NamedTextColor.GRAY))
                    .append(Component.text("LUCKY!", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true))
                    .build());

            // Lucky sparkle effect
            player.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    player.getLocation().add(0, 1, 0),
                    10,
                    0.3, 0.3, 0.3,
                    0
            );
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        } else {
            player.sendActionBar(Component.text()
                    .append(Component.text("+" + MessageUtil.formatCurrency(finalPrice), NamedTextColor.GREEN))
                    .append(Component.text(" (" + amount + "x " + materialName + ")", NamedTextColor.GRAY))
                    .build());
        }
    }
}
