package com.overworldcraterewards.features.vacuumhopper;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.data.PDCKeys;
import com.overworldcraterewards.items.CustomItemType;
import com.overworldcraterewards.util.InventoryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles block place/break, GUI interactions, and link item usage
 * for the Vacuum Void Hopper.
 */
public class VacuumHopperListener implements Listener {

    private final OverworldCrateRewardsPlugin plugin;
    private final VacuumHopperManager manager;

    // Track which players have the hopper GUI open and which hopper
    private final java.util.Map<java.util.UUID, Location> openGUIs = new java.util.HashMap<>();

    public VacuumHopperListener(OverworldCrateRewardsPlugin plugin, VacuumHopperManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // === Block Place: Register a new hopper ===

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!InventoryUtil.isCustomItem(item, CustomItemType.VACUUM_VOID_HOPPER)) {
            return;
        }

        Player player = event.getPlayer();
        Location loc = event.getBlockPlaced().getLocation();

        // Read filter/links from item PDC if exists (persistence across pickup/place)
        Set<Material> filter = new HashSet<>();
        List<Location> links = new ArrayList<>();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            // Deserialize filter
            String filterStr = pdc.get(PDCKeys.VACUUM_HOPPER_FILTER, PersistentDataType.STRING);
            if (filterStr != null && !filterStr.isEmpty()) {
                for (String matName : filterStr.split(",")) {
                    try {
                        filter.add(Material.valueOf(matName.trim()));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            // Deserialize links
            String linksStr = pdc.get(PDCKeys.VACUUM_HOPPER_LINKS, PersistentDataType.STRING);
            if (linksStr != null && !linksStr.isEmpty()) {
                for (String linkKey : linksStr.split(";")) {
                    Location linkLoc = VacuumHopperManager.parseLocationKey(linkKey.trim());
                    if (linkLoc != null) {
                        links.add(linkLoc);
                    }
                }
            }
        }

        // Register the hopper
        manager.addHopper(loc, player.getUniqueId(), filter, links);

        // Placement effect
        player.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.2f);
        player.getWorld().spawnParticle(
                Particle.PORTAL,
                loc.clone().add(0.5, 0.5, 0.5),
                20,
                0.3, 0.3, 0.3,
                0.5
        );

        player.sendActionBar(Component.text("Vacuum Void Hopper placed! Right-click to configure.", NamedTextColor.LIGHT_PURPLE));
    }

    // === Block Break: Unregister and drop custom item ===

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();

        if (!manager.isHopper(loc)) {
            return;
        }

        event.setDropItems(false); // Don't drop vanilla lodestone

        VacuumHopperManager.HopperData data = manager.removeHopper(loc);
        Player player = event.getPlayer();

        // Create the custom item with filter/links serialized in PDC
        ItemStack hopperItem = plugin.getItemManager().createItem(CustomItemType.VACUUM_VOID_HOPPER);
        ItemMeta meta = hopperItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Serialize filter
        if (data != null && !data.getVoidFilter().isEmpty()) {
            StringBuilder filterSb = new StringBuilder();
            for (Material mat : data.getVoidFilter()) {
                if (filterSb.length() > 0) filterSb.append(",");
                filterSb.append(mat.name());
            }
            pdc.set(PDCKeys.VACUUM_HOPPER_FILTER, PersistentDataType.STRING, filterSb.toString());
        }

        // Serialize links
        if (data != null && !data.getLinkedChests().isEmpty()) {
            StringBuilder linksSb = new StringBuilder();
            for (Location linkLoc : data.getLinkedChests()) {
                if (linksSb.length() > 0) linksSb.append(";");
                linksSb.append(VacuumHopperManager.toLocationKey(linkLoc));
            }
            pdc.set(PDCKeys.VACUUM_HOPPER_LINKS, PersistentDataType.STRING, linksSb.toString());
        }

        hopperItem.setItemMeta(meta);

        // Drop the custom item
        event.getBlock().getWorld().dropItemNaturally(loc.add(0.5, 0.5, 0.5), hopperItem);

        player.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.0f);
        player.sendActionBar(Component.text("Vacuum Void Hopper removed.", NamedTextColor.LIGHT_PURPLE));
    }

    // === Right-Click Lodestone: Open GUI ===

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LODESTONE) return;

        Location loc = block.getLocation();
        if (!manager.isHopper(loc)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        // Check for link item usage (shift+right-click with link item on a container)
        // This is handled in a separate event below

        VacuumHopperManager.HopperData data = manager.getHopper(loc);
        Inventory gui = VacuumHopperGUI.createGUI(loc, data, manager);
        openGUIs.put(player.getUniqueId(), loc);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.2f);
    }

    // === Link Item: Shift+Right-Click a container to link it ===

    @EventHandler(priority = EventPriority.HIGH)
    public void onLinkInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!VacuumHopperGUI.isLinkItem(item)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Check if target is a container
        if (!(block.getState() instanceof Container)) {
            player.sendActionBar(Component.text("That's not a container!", NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);

        // Get hopper location from link item
        String hopperKey = VacuumHopperGUI.getLinkItemHopperKey(item);
        if (hopperKey == null) return;

        Location hopperLoc = VacuumHopperManager.parseLocationKey(hopperKey);
        if (hopperLoc == null || !manager.isHopper(hopperLoc)) {
            player.sendActionBar(Component.text("Hopper no longer exists!", NamedTextColor.RED));
            return;
        }

        // Add the link
        boolean success = manager.addLink(hopperLoc, block.getLocation());

        if (success) {
            player.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
            player.getWorld().spawnParticle(
                    Particle.ENCHANT,
                    block.getLocation().add(0.5, 1.0, 0.5),
                    15,
                    0.3, 0.3, 0.3,
                    0.5
            );
            VacuumHopperManager.HopperData data = manager.getHopper(hopperLoc);
            int linkCount = data != null ? data.getLinkedChests().size() : 0;
            int maxLinks = plugin.getConfigManager().getVacuumHopperMaxLinks();
            player.sendActionBar(Component.text("✓ Chest linked! (" + linkCount + "/" + maxLinks + ")", NamedTextColor.GREEN));
        } else {
            player.sendActionBar(Component.text("Already linked or max links reached!", NamedTextColor.RED));
            player.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
        }
    }

    // === GUI Click Handling ===

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Location hopperLoc = openGUIs.get(player.getUniqueId());
        if (hopperLoc == null) return;

        // Check if it's our GUI (by title)
        Component title = event.getView().title();
        if (title == null) return;
        // Simple check: if player has an open GUI tracked, handle it

        int slot = event.getRawSlot();

        // Allow link item to be taken/placed in slot 4
        if (slot == VacuumHopperGUI.LINK_ITEM_SLOT) {
            // Allow taking/placing the link item
            return;
        }

        // Static slots: cancel all interaction
        if (slot >= 0 && slot < 54 && VacuumHopperGUI.isStaticSlot(slot)) {
            event.setCancelled(true);
            return;
        }

        // Filter area clicks
        if (slot >= 0 && slot < 54 && VacuumHopperGUI.isFilterSlot(slot)) {
            event.setCancelled(true);

            VacuumHopperManager.HopperData data = manager.getHopper(hopperLoc);
            if (data == null) return;

            ItemStack currentSlotItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();

            if (currentSlotItem != null && currentSlotItem.getType() != Material.AIR) {
                // Clicking an existing filter item → remove it from filter
                Material filterMat = currentSlotItem.getType();
                data.getVoidFilter().remove(filterMat);
                event.getInventory().setItem(slot, null);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
                player.sendActionBar(Component.text("Removed ", NamedTextColor.RED)
                        .append(Component.text(com.overworldcraterewards.util.MessageUtil.formatMaterialName(filterMat.name()), NamedTextColor.WHITE))
                        .append(Component.text(" from void filter", NamedTextColor.RED)));
                manager.saveData();
            } else if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                // Clicking empty slot with item on cursor → add to filter
                Material filterMat = cursorItem.getType();
                if (!data.getVoidFilter().contains(filterMat)) {
                    data.getVoidFilter().add(filterMat);

                    // Create display item (don't consume player's item)
                    ItemStack displayItem = new ItemStack(filterMat);
                    ItemMeta meta = displayItem.getItemMeta();
                    meta.displayName(Component.text(com.overworldcraterewards.util.MessageUtil.formatMaterialName(filterMat.name()), NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("✖ VOIDED", NamedTextColor.RED)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Click to remove from filter", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    displayItem.setItemMeta(meta);

                    event.getInventory().setItem(slot, displayItem);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);
                    player.sendActionBar(Component.text("Added ", NamedTextColor.GREEN)
                            .append(Component.text(com.overworldcraterewards.util.MessageUtil.formatMaterialName(filterMat.name()), NamedTextColor.WHITE))
                            .append(Component.text(" to void filter", NamedTextColor.GREEN)));
                    manager.saveData();
                }
            }
            return;
        }

        // Clicks in player inventory while GUI is open — allow normally
        // (player can pick up items from their inventory to use as filter templates)
    }

    /**
     * Handle GUI close: remove from tracking, save data.
     */
    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Location hopperLoc = openGUIs.remove(player.getUniqueId());
        if (hopperLoc != null) {
            // Check if link item was placed back
            // (This is handled automatically since the link item stays in the GUI)
            manager.saveData();
        }
    }

    public VacuumHopperManager getManager() {
        return manager;
    }
}
