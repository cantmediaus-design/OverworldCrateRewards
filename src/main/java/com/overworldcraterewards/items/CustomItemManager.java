package com.overworldcraterewards.items;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.config.ConfigManager;
import com.overworldcraterewards.data.PDCKeys;
import com.overworldcraterewards.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages creation and identification of custom items.
 */
public class CustomItemManager {

    private final OverworldCrateRewardsPlugin plugin;
    private final ConfigManager configManager;

    public CustomItemManager(OverworldCrateRewardsPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Create a custom item of the specified type.
     * @param type The type of item to create
     * @return The created ItemStack
     */
    public ItemStack createItem(CustomItemType type) {
        return createItem(type, 1);
    }

    /**
     * Create custom items of the specified type.
     * @param type The type of item to create
     * @param amount The number of items
     * @return The created ItemStack
     */
    public ItemStack createItem(CustomItemType type, int amount) {
        ItemStack item = new ItemStack(type.getMaterial(), amount);
        ItemMeta meta = item.getItemMeta();

        // Set display name
        String displayName = configManager.getItemDisplayName(type);
        meta.displayName(MessageUtil.colorize(displayName));

        // Set lore
        List<String> loreStrings = configManager.getItemLore(type);
        if (loreStrings.isEmpty()) {
            loreStrings = getDefaultLore(type);
        }
        meta.lore(MessageUtil.colorize(loreStrings));

        // Hide vanilla bundle tooltip for Farmer's Pouch (hides "Can hold a mixed stack" and "Empty")
        if (type == CustomItemType.FARMERS_POUCH) {
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        }

        // Make Miner's Fervor, Jack'o'Hammer, and Melon-nator unbreakable
        if (type == CustomItemType.MINERS_FERVOR || type == CustomItemType.JACKO_HAMMER
                || type == CustomItemType.MELON_NATOR) {
            meta.setUnbreakable(true);
        }

        // Farmer's Hand: dye yellow + enchant glint + hide dye
        if (type == CustomItemType.FARMERS_HAND && meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(Color.fromRGB(255, 215, 0)); // Gold/straw yellow
            leatherMeta.setEnchantmentGlintOverride(true);
            leatherMeta.addItemFlags(ItemFlag.HIDE_DYE);
        }

        // Vacuum Void Hopper: unstackable + enchant glint
        if (type == CustomItemType.VACUUM_VOID_HOPPER) {
            meta.setMaxStackSize(1);
            meta.setEnchantmentGlintOverride(true);
        }

        // Mark as custom item
        meta.getPersistentDataContainer().set(PDCKeys.ITEM_TYPE, PersistentDataType.STRING, type.getId());

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Check if an ItemStack is a specific custom item type.
     * @param item The item to check
     * @param type The expected type
     * @return true if the item matches the type
     */
    public boolean isCustomItem(ItemStack item, CustomItemType type) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        String itemType = item.getItemMeta().getPersistentDataContainer()
                .get(PDCKeys.ITEM_TYPE, PersistentDataType.STRING);

        return type.getId().equals(itemType);
    }

    /**
     * Get the CustomItemType of an ItemStack.
     * @param item The item to check
     * @return The CustomItemType, or null if not a custom item
     */
    public CustomItemType getItemType(ItemStack item) {
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

    /**
     * Get default lore for an item type (used if not configured).
     */
    private List<String> getDefaultLore(CustomItemType type) {
        List<String> lore = new ArrayList<>();

        switch (type) {
            case HARVEST_HOE -> {
                lore.add("&7Right-click to harvest a 3x3 area");
                lore.add("&7Automatically replants crops");
                lore.add("");
                lore.add("&eRare Tool");
            }
            case FARMERS_POUCH -> {
                lore.add("&7Status: &a&lENABLED");
                lore.add("");
                lore.add("&7Auto-sells crops on pickup");
                lore.add("&8Shift+Right-Click to toggle");
            }
            case SOUL_SIPHON -> {
                lore.add("&7Keep in your inventory");
                lore.add("&7+$1 per manual mob kill");
                lore.add("");
                lore.add("&eRare Accessory");
            }
            case MINERS_FERVOR -> {
                lore.add("&7Mine blocks to build your streak");
                lore.add("&7+$0.10 per block mined");
                lore.add("&7Streak = +0.1% mining speed per point");
                lore.add("");
                lore.add("&bRare Tool");
            }
            case JACKO_HAMMER -> {
                lore.add("&7Instantly breaks pumpkins");
                lore.add("&7~25% bonus pumpkin seed drop");
                lore.add("");
                lore.add("&eRare Tool");
            }
            case ANGLERS_CHARM -> {
                lore.add("&7Status: &a&lENABLED");
                lore.add("");
                lore.add("&7Auto-sells fish on catch");
                lore.add("&710% double catch chance");
                lore.add("&8Shift+Right-Click to toggle");
            }
            case LUMBERJACKS_MARK -> {
                lore.add("&7Keep in your inventory");
                lore.add("&7~15% bonus stripped log on log break");
                lore.add("&7+$0.25 per bonus log");
                lore.add("");
                lore.add("&eRare Accessory");
            }
            case MELON_NATOR -> {
                lore.add("&7Instantly breaks melons");
                lore.add("&7Evolving glistering melon chance");
                lore.add("");
                lore.add("&eRare Tool");
            }
            case FARMERS_HAND -> {
                lore.add("&7Status: &a&lENABLED");
                lore.add("");
                lore.add("&7Summons an Allay companion");
                lore.add("&7Magnetizes nearby dropped items");
                lore.add("&8Shift+Right-Click to toggle");
            }
            case VACUUM_VOID_HOPPER -> {
                lore.add("&7Place to activate");
                lore.add("&7Collects items in a radius");
                lore.add("&7Right-click to configure");
                lore.add("");
                lore.add("&dRare Block");
            }
        }

        return lore;
    }
}
