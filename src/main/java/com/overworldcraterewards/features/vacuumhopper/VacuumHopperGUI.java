package com.overworldcraterewards.features.vacuumhopper;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates and manages the Vacuum Void Hopper GUI.
 *
 * Layout (54 slots, 6 rows):
 * Row 1 (0-8):   Border | Border | Border | Border | LINK ITEM | Border | Border | Border | Border
 * Row 2-5 (9-44): Void filter area (36 slots) - click with items to toggle filter
 * Row 6 (45-53): Border | Info | Info | Border | Border | Border | Info | Info | Border
 */
public class VacuumHopperGUI {

    public static final String GUI_TITLE_PREFIX = "Vacuum Void Hopper";
    public static final int LINK_ITEM_SLOT = 4;

    // Filter area: rows 2-5 (slots 9-44)
    public static final int FILTER_START = 9;
    public static final int FILTER_END = 44;

    // Info slots in bottom row
    public static final int INFO_COLLECTED_SLOT = 46;
    public static final int INFO_VOIDED_SLOT = 47;
    public static final int INFO_LINKS_SLOT = 51;
    public static final int INFO_STATUS_SLOT = 52;

    /**
     * Create and open the GUI for a placed hopper.
     */
    public static Inventory createGUI(Location hopperLoc, VacuumHopperManager.HopperData data,
                                       VacuumHopperManager manager) {
        Inventory gui = Bukkit.createInventory(null, 54,
                Component.text(GUI_TITLE_PREFIX, NamedTextColor.DARK_PURPLE)
                        .decoration(TextDecoration.BOLD, true));

        // Fill borders with gray glass panes
        ItemStack border = createBorderItem();
        for (int i = 0; i < 9; i++) {
            if (i != LINK_ITEM_SLOT) {
                gui.setItem(i, border);
            }
        }
        for (int i = 45; i < 54; i++) {
            if (i != INFO_COLLECTED_SLOT && i != INFO_VOIDED_SLOT
                    && i != INFO_LINKS_SLOT && i != INFO_STATUS_SLOT) {
                gui.setItem(i, border);
            }
        }

        // Link Item in slot 4
        gui.setItem(LINK_ITEM_SLOT, createLinkItem(hopperLoc));

        // Populate filter slots with current filter items as display
        int slot = FILTER_START;
        for (Material mat : data.getVoidFilter()) {
            if (slot > FILTER_END) break;
            gui.setItem(slot, createFilterDisplayItem(mat));
            slot++;
        }

        // Info items
        gui.setItem(INFO_COLLECTED_SLOT, createInfoItem(Material.HOPPER, "Items Collected",
                MessageUtil.formatNumber(data.getItemsCollected()), NamedTextColor.GREEN));
        gui.setItem(INFO_VOIDED_SLOT, createInfoItem(Material.BARRIER, "Items Voided",
                MessageUtil.formatNumber(data.getItemsVoided()), NamedTextColor.RED));
        gui.setItem(INFO_LINKS_SLOT, createInfoItem(Material.TRIPWIRE_HOOK, "Linked Chests",
                data.getLinkedChests().size() + "/" + OverworldCrateRewardsPlugin.getInstance()
                        .getConfigManager().getVacuumHopperMaxLinks(), NamedTextColor.AQUA));
        gui.setItem(INFO_STATUS_SLOT, createInfoItem(Material.LIME_DYE, "Status", "Active", NamedTextColor.GREEN));

        return gui;
    }

    /**
     * Create the link item (Tripwire Hook).
     */
    public static ItemStack createLinkItem(Location hopperLoc) {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("⚡ Hopper Link", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Take this item out", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Shift+Right-Click a chest to link", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Place it back to save links", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Hopper: " + VacuumHopperManager.toLocationKey(hopperLoc), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.setEnchantmentGlintOverride(true);

        // Store hopper location in PDC for reference
        meta.getPersistentDataContainer().set(
                com.overworldcraterewards.data.PDCKeys.VACUUM_HOPPER_LINKS,
                org.bukkit.persistence.PersistentDataType.STRING,
                VacuumHopperManager.toLocationKey(hopperLoc)
        );

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Check if an item is a hopper link item.
     */
    public static boolean isLinkItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(com.overworldcraterewards.data.PDCKeys.VACUUM_HOPPER_LINKS,
                        org.bukkit.persistence.PersistentDataType.STRING);
    }

    /**
     * Get the hopper location from a link item.
     */
    public static String getLinkItemHopperKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(com.overworldcraterewards.data.PDCKeys.VACUUM_HOPPER_LINKS,
                        org.bukkit.persistence.PersistentDataType.STRING);
    }

    private static ItemStack createBorderItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createFilterDisplayItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(MessageUtil.formatMaterialName(material.name()), NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("✖ VOIDED", NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Click to remove from filter", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createInfoItem(Material material, String title, String value, NamedTextColor valueColor) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title, NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(value, valueColor)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Check if a slot is in the filter area.
     */
    public static boolean isFilterSlot(int slot) {
        return slot >= FILTER_START && slot <= FILTER_END;
    }

    /**
     * Check if a slot is a border or info slot (should not be interactable).
     */
    public static boolean isStaticSlot(int slot) {
        if (slot < FILTER_START && slot != LINK_ITEM_SLOT) return true; // Top border
        if (slot > FILTER_END && slot != INFO_COLLECTED_SLOT && slot != INFO_VOIDED_SLOT
                && slot != INFO_LINKS_SLOT && slot != INFO_STATUS_SLOT) return true; // Bottom border
        if (slot == INFO_COLLECTED_SLOT || slot == INFO_VOIDED_SLOT
                || slot == INFO_LINKS_SLOT || slot == INFO_STATUS_SLOT) return true; // Info items
        return false;
    }
}
