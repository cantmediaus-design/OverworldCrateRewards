package com.overworldcraterewards.util;

import com.overworldcraterewards.data.PDCKeys;
import com.overworldcraterewards.items.CustomItemType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Utility methods for inventory operations.
 */
public final class InventoryUtil {

    private InventoryUtil() {} // Utility class

    /**
     * Check if a player has a specific custom item type in their inventory.
     * @param player The player to check
     * @param type The item type to look for
     * @return true if the player has at least one of the item type
     */
    public static boolean hasItemInInventory(Player player, CustomItemType type) {
        return findItemInInventory(player, type) != null;
    }

    /**
     * Find a specific custom item type in a player's inventory.
     * @param player The player to check
     * @param type The item type to look for
     * @return The ItemStack if found, null otherwise
     */
    public static ItemStack findItemInInventory(Player player, CustomItemType type) {
        // Check main inventory + hotbar
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCustomItem(item, type)) {
                return item;
            }
        }
        // Check off-hand slot (not included in getContents())
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isCustomItem(offHand, type)) {
            return offHand;
        }
        return null;
    }

    /**
     * Check if an ItemStack is a specific custom item type.
     * @param item The item to check
     * @param type The expected type
     * @return true if the item matches the type
     */
    public static boolean isCustomItem(ItemStack item, CustomItemType type) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        String itemType = item.getItemMeta().getPersistentDataContainer()
                .get(PDCKeys.ITEM_TYPE, PersistentDataType.STRING);

        return type.getId().equals(itemType);
    }

    /**
     * Check if an ItemStack is any custom item from this plugin.
     * @param item The item to check
     * @return The CustomItemType if it's a custom item, null otherwise
     */
    public static CustomItemType getCustomItemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        String itemTypeId = item.getItemMeta().getPersistentDataContainer()
                .get(PDCKeys.ITEM_TYPE, PersistentDataType.STRING);

        if (itemTypeId == null) {
            return null;
        }

        return CustomItemType.fromId(itemTypeId);
    }
}
