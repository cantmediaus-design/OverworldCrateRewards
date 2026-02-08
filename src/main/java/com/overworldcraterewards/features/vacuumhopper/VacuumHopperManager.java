package com.overworldcraterewards.features.vacuumhopper;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Manages all placed Vacuum Void Hoppers in the world.
 * Handles persistence, vacuum collection, item transfer, void filtering,
 * and particle effects.
 */
public class VacuumHopperManager {

    private final OverworldCrateRewardsPlugin plugin;
    private final ConfigManager config;

    // Runtime data for all placed hoppers, keyed by location string "world:x:y:z"
    private final Map<String, HopperData> placedHoppers = new HashMap<>();

    // Tick tasks
    private BukkitTask vacuumTask;
    private BukkitTask particleTask;

    // Data file
    private File dataFile;

    public VacuumHopperManager(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.dataFile = new File(plugin.getDataFolder(), "vacuum-hoppers.yml");

        loadData();
        startTasks();
    }

    /**
     * Data class for a placed hopper.
     */
    public static class HopperData {
        private UUID owner;
        private Set<Material> voidFilter = new HashSet<>();
        private List<Location> linkedChests = new ArrayList<>();
        private long itemsCollected = 0;
        private long itemsVoided = 0;

        public UUID getOwner() { return owner; }
        public void setOwner(UUID owner) { this.owner = owner; }
        public Set<Material> getVoidFilter() { return voidFilter; }
        public List<Location> getLinkedChests() { return linkedChests; }
        public long getItemsCollected() { return itemsCollected; }
        public void setItemsCollected(long count) { this.itemsCollected = count; }
        public long getItemsVoided() { return itemsVoided; }
        public void setItemsVoided(long count) { this.itemsVoided = count; }
    }

    /**
     * Start the repeating tasks for vacuum collection and particles.
     */
    private void startTasks() {
        int tickInterval = config.getVacuumHopperTickInterval();

        // Vacuum + transfer task
        vacuumTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, HopperData> entry : new HashMap<>(placedHoppers).entrySet()) {
                    Location loc = parseLocationKey(entry.getKey());
                    if (loc == null || !loc.isWorldLoaded()) continue;

                    // Check if chunk is loaded
                    if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                        continue;
                    }

                    // Verify the block is still a lodestone
                    Block block = loc.getBlock();
                    if (block.getType() != Material.LODESTONE) {
                        // Block was broken by something else, remove data
                        placedHoppers.remove(entry.getKey());
                        continue;
                    }

                    HopperData data = entry.getValue();
                    int radius = config.getVacuumHopperRadius();
                    int transferRate = config.getVacuumHopperTransferRate();
                    int itemsTransferred = 0;

                    // Collect nearby items
                    for (Entity entity : loc.getWorld().getNearbyEntities(loc.clone().add(0.5, 0.5, 0.5), radius, radius, radius)) {
                        if (!(entity instanceof Item itemEntity)) continue;
                        if (itemEntity.isDead()) continue;
                        if (itemEntity.getPickupDelay() > 40) continue; // Skip recently dropped items with long delay

                        ItemStack itemStack = itemEntity.getItemStack();

                        // Check void filter
                        if (data.getVoidFilter().contains(itemStack.getType())) {
                            // Void this item
                            data.setItemsVoided(data.getItemsVoided() + itemStack.getAmount());
                            itemEntity.remove();
                            continue;
                        }

                        // Try to transfer to linked chests
                        boolean transferred = false;
                        for (Location chestLoc : new ArrayList<>(data.getLinkedChests())) {
                            if (chestLoc == null || !chestLoc.isWorldLoaded()) continue;
                            if (!chestLoc.getWorld().isChunkLoaded(chestLoc.getBlockX() >> 4, chestLoc.getBlockZ() >> 4)) {
                                continue;
                            }

                            Block chestBlock = chestLoc.getBlock();
                            if (!(chestBlock.getState() instanceof Container container)) {
                                // Chest was broken, remove link
                                data.getLinkedChests().remove(chestLoc);
                                continue;
                            }

                            Inventory inv = container.getInventory();
                            HashMap<Integer, ItemStack> remaining = inv.addItem(itemStack.clone());

                            if (remaining.isEmpty()) {
                                // Fully transferred
                                itemEntity.remove();
                                transferred = true;
                                data.setItemsCollected(data.getItemsCollected() + itemStack.getAmount());
                                itemsTransferred += itemStack.getAmount();
                                break;
                            } else {
                                // Partially transferred
                                int transferredCount = itemStack.getAmount() - remaining.values().stream()
                                        .mapToInt(ItemStack::getAmount).sum();
                                if (transferredCount > 0) {
                                    data.setItemsCollected(data.getItemsCollected() + transferredCount);
                                    itemsTransferred += transferredCount;
                                    // Update the item entity with remaining amount
                                    ItemStack leftover = remaining.values().iterator().next();
                                    itemEntity.setItemStack(leftover);
                                }
                                // Try next chest for overflow
                            }
                        }

                        // If no linked chests or all full, pull item toward hopper but don't delete it
                        if (!transferred && !data.getLinkedChests().isEmpty()) {
                            // Item stays in world, can be picked up by player
                        } else if (!transferred) {
                            // No linked chests — just pull toward hopper location
                            double distance = itemEntity.getLocation().distance(loc.clone().add(0.5, 0.5, 0.5));
                            if (distance > 1.5) {
                                org.bukkit.util.Vector direction = loc.clone().add(0.5, 0.5, 0.5).toVector()
                                        .subtract(itemEntity.getLocation().toVector()).normalize();
                                itemEntity.setVelocity(direction.multiply(0.3));
                            }
                        }

                        if (itemsTransferred >= transferRate) break;
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, tickInterval);

        // Particle task: pulsing aura every 40 ticks (2 seconds)
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, HopperData> entry : placedHoppers.entrySet()) {
                    Location loc = parseLocationKey(entry.getKey());
                    if (loc == null || !loc.isWorldLoaded()) continue;
                    if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

                    // Check if any player is nearby (don't waste particles)
                    boolean playerNearby = false;
                    for (org.bukkit.entity.Player p : loc.getWorld().getPlayers()) {
                        if (p.getLocation().distance(loc) < 48) {
                            playerNearby = true;
                            break;
                        }
                    }
                    if (!playerNearby) continue;

                    // Pulsing ring particles
                    double radius = config.getVacuumHopperRadius();
                    Location center = loc.clone().add(0.5, 0.5, 0.5);
                    int points = 16;
                    for (int i = 0; i < points; i++) {
                        double angle = (2 * Math.PI / points) * i;
                        double x = center.getX() + radius * Math.cos(angle);
                        double z = center.getZ() + radius * Math.sin(angle);
                        Location particleLoc = new Location(loc.getWorld(), x, center.getY(), z);
                        loc.getWorld().spawnParticle(
                                Particle.PORTAL,
                                particleLoc,
                                1,
                                0, 0, 0,
                                0.01
                        );
                    }

                    // Center vortex
                    loc.getWorld().spawnParticle(
                            Particle.WITCH,
                            center,
                            3,
                            0.2, 0.2, 0.2,
                            0.01
                    );
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    // === Hopper Registration ===

    public void addHopper(Location location, UUID owner, Set<Material> filter, List<Location> links) {
        String key = toLocationKey(location);
        HopperData data = new HopperData();
        data.setOwner(owner);
        data.getVoidFilter().addAll(filter);
        data.getLinkedChests().addAll(links);
        placedHoppers.put(key, data);
        saveData();
    }

    public HopperData removeHopper(Location location) {
        HopperData data = placedHoppers.remove(toLocationKey(location));
        saveData();
        return data;
    }

    public HopperData getHopper(Location location) {
        return placedHoppers.get(toLocationKey(location));
    }

    public boolean isHopper(Location location) {
        return placedHoppers.containsKey(toLocationKey(location));
    }

    // === Link Management ===

    public boolean addLink(Location hopperLoc, Location chestLoc) {
        HopperData data = getHopper(hopperLoc);
        if (data == null) return false;

        int maxLinks = config.getVacuumHopperMaxLinks();
        if (data.getLinkedChests().size() >= maxLinks) return false;

        // Check for duplicate
        for (Location existing : data.getLinkedChests()) {
            if (existing.getBlockX() == chestLoc.getBlockX()
                    && existing.getBlockY() == chestLoc.getBlockY()
                    && existing.getBlockZ() == chestLoc.getBlockZ()
                    && existing.getWorld().equals(chestLoc.getWorld())) {
                return false; // Already linked
            }
        }

        data.getLinkedChests().add(chestLoc.clone());
        saveData();
        return true;
    }

    // === Persistence ===

    private void loadData() {
        if (!dataFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = yaml.getConfigurationSection("hoppers");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection hopperSection = section.getConfigurationSection(key);
            if (hopperSection == null) continue;

            HopperData data = new HopperData();

            String ownerStr = hopperSection.getString("owner");
            if (ownerStr != null) {
                try {
                    data.setOwner(UUID.fromString(ownerStr));
                } catch (IllegalArgumentException ignored) {}
            }

            // Load void filter
            List<String> filterList = hopperSection.getStringList("filter");
            for (String mat : filterList) {
                try {
                    data.getVoidFilter().add(Material.valueOf(mat));
                } catch (IllegalArgumentException ignored) {}
            }

            // Load linked chests
            List<String> linkList = hopperSection.getStringList("links");
            for (String linkStr : linkList) {
                Location linkLoc = parseLocationKey(linkStr);
                if (linkLoc != null) {
                    data.getLinkedChests().add(linkLoc);
                }
            }

            data.setItemsCollected(hopperSection.getLong("items-collected", 0));
            data.setItemsVoided(hopperSection.getLong("items-voided", 0));

            placedHoppers.put(key, data);
        }

        plugin.getLogger().info("Loaded " + placedHoppers.size() + " vacuum hoppers.");
    }

    public void saveData() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<String, HopperData> entry : placedHoppers.entrySet()) {
            String path = "hoppers." + entry.getKey().replace(":", "_");
            HopperData data = entry.getValue();

            yaml.set(path + ".location", entry.getKey());
            if (data.getOwner() != null) {
                yaml.set(path + ".owner", data.getOwner().toString());
            }

            List<String> filterNames = new ArrayList<>();
            for (Material mat : data.getVoidFilter()) {
                filterNames.add(mat.name());
            }
            yaml.set(path + ".filter", filterNames);

            List<String> linkStrings = new ArrayList<>();
            for (Location loc : data.getLinkedChests()) {
                linkStrings.add(toLocationKey(loc));
            }
            yaml.set(path + ".links", linkStrings);

            yaml.set(path + ".items-collected", data.getItemsCollected());
            yaml.set(path + ".items-voided", data.getItemsVoided());
        }

        try {
            yaml.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save vacuum hopper data: " + e.getMessage());
        }
    }

    // === Utility ===

    public static String toLocationKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public static Location parseLocationKey(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) return null;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Shutdown: cancel tasks and save data.
     */
    public void shutdown() {
        if (vacuumTask != null) vacuumTask.cancel();
        if (particleTask != null) particleTask.cancel();
        saveData();
    }
}
