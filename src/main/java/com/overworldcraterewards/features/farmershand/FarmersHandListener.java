package com.overworldcraterewards.features.farmershand;

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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the Farmer's Hand - a yellow leather helmet that spawns an Allay companion
 * and acts as an item magnet.
 *
 * Features:
 * - Equip: spawns an Allay companion near the player
 * - Unequip/death/logout: despawns the Allay
 * - Item magnet: nearby dropped items are pulled toward the player
 * - Toggle: Shift+Right-Click to enable/disable
 * - Stat tracking: items collected by magnet
 *
 * Easter Egg: "The Scarecrow's Vigil"
 * - Stand completely still for 5 minutes in a wheat crop field
 * - Upgrades to "Golden Harvest Crown" with expanded radius + golden Allay particles
 */
public class FarmersHandListener implements Listener {

    private final OverworldCrateRewardsPlugin plugin;
    private final ConfigManager config;

    // Track active Allays per player UUID
    private final Map<UUID, UUID> playerAllays = new HashMap<>();

    // Track scarecrow vigil: ticks standing still
    private final Map<UUID, Long> scarecrowTicks = new HashMap<>();
    private final Map<UUID, Location> scarecrowLastPos = new HashMap<>();

    // Tick tasks
    private BukkitTask magnetTask;
    private BukkitTask scarecrowTask;
    private BukkitTask allayFollowTask;

    public FarmersHandListener(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        startTasks();
    }

    /**
     * Start the repeating tasks for magnet, scarecrow tracking, and Allay follow.
     */
    private void startTasks() {
        // Magnet task: every 4 ticks, pull items toward players wearing the hat
        magnetTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack helmet = player.getInventory().getHelmet();
                    if (!InventoryUtil.isCustomItem(helmet, CustomItemType.FARMERS_HAND)) {
                        continue;
                    }
                    if (isDisabled(helmet)) {
                        continue;
                    }

                    boolean upgraded = isUpgraded(helmet);
                    int radius = upgraded ? config.getFarmersHandUpgradedRadius() : config.getFarmersHandMagnetRadius();

                    // Pull nearby items
                    int itemsCollected = 0;
                    for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                        if (!(entity instanceof Item itemEntity)) {
                            continue;
                        }
                        // Skip items with pickup delay
                        if (itemEntity.getPickupDelay() > 0) {
                            continue;
                        }

                        // Teleport/pull item toward player
                        Location itemLoc = itemEntity.getLocation();
                        Location playerLoc = player.getLocation().add(0, 0.5, 0);
                        double distance = itemLoc.distance(playerLoc);

                        if (distance < config.getFarmersHandItemPullThreshold()) {
                            // Close enough, let vanilla pickup handle it
                            continue;
                        }

                        // Pull toward player with velocity
                        org.bukkit.util.Vector direction = playerLoc.toVector().subtract(itemLoc.toVector()).normalize();
                        double speed = Math.min(config.getFarmersHandPullSpeedMax(), config.getFarmersHandPullSpeedBase() + (distance * config.getFarmersHandPullSpeedPerBlock()));
                        itemEntity.setVelocity(direction.multiply(speed));
                        itemsCollected++;
                    }

                    // Update stats if items were pulled
                    if (itemsCollected > 0) {
                        updateItemsCollected(player, helmet, itemsCollected);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, config.getFarmersHandMagnetTaskInterval());

        // Allay follow task: every 10 ticks, teleport Allay if too far
        allayFollowTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, UUID> entry : new HashMap<>(playerAllays).entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) {
                        // Player left, clean up
                        removeAllay(entry.getKey());
                        continue;
                    }

                    Entity allay = Bukkit.getEntity(entry.getValue());
                    if (allay == null || allay.isDead()) {
                        // Allay is gone, respawn if still wearing hat
                        playerAllays.remove(entry.getKey());
                        ItemStack helmet = player.getInventory().getHelmet();
                        if (InventoryUtil.isCustomItem(helmet, CustomItemType.FARMERS_HAND)
                                && !isDisabled(helmet)) {
                            spawnAllay(player);
                        }
                        continue;
                    }

                    // Teleport if too far
                    double distance = allay.getLocation().distance(player.getLocation());
                    if (distance > config.getFarmersHandAllayFollowDistance()) {
                        allay.teleport(player.getLocation().add(1, 1, 0));
                    }

                    // Upgraded: golden trail particles on Allay
                    if (InventoryUtil.isCustomItem(player.getInventory().getHelmet(), CustomItemType.FARMERS_HAND)
                            && isUpgraded(player.getInventory().getHelmet())) {
                        player.getWorld().spawnParticle(
                                Particle.WAX_ON,
                                allay.getLocation().add(0, 0.3, 0),
                                3,
                                0.1, 0.1, 0.1,
                                0.01
                        );
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, config.getFarmersHandAllayFollowInterval());

        // Scarecrow vigil task: every 20 ticks (1 second), check players
        scarecrowTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack helmet = player.getInventory().getHelmet();
                    if (!InventoryUtil.isCustomItem(helmet, CustomItemType.FARMERS_HAND)) {
                        scarecrowTicks.remove(player.getUniqueId());
                        scarecrowLastPos.remove(player.getUniqueId());
                        continue;
                    }
                    if (isUpgraded(helmet)) {
                        scarecrowTicks.remove(player.getUniqueId());
                        continue;
                    }

                    // Check if player has moved
                    Location lastPos = scarecrowLastPos.get(player.getUniqueId());
                    Location currentPos = player.getLocation();

                    if (lastPos != null && hasMoved(lastPos, currentPos)) {
                        // Player moved, reset counter
                        scarecrowTicks.put(player.getUniqueId(), 0L);
                    } else if (lastPos == null) {
                        scarecrowTicks.put(player.getUniqueId(), 0L);
                    }

                    scarecrowLastPos.put(player.getUniqueId(), currentPos.clone());

                    // Increment if not moved
                    long ticks = scarecrowTicks.getOrDefault(player.getUniqueId(), 0L) + 1;
                    scarecrowTicks.put(player.getUniqueId(), ticks);

                    // Check threshold (minutes * 60 seconds)
                    long requiredSeconds = config.getFarmersHandScarecrowMinutes() * 60L;
                    if (ticks >= requiredSeconds) {
                        // Check if standing in a wheat field
                        if (isInWheatField(player)) {
                            upgradeHand(player, helmet);
                            scarecrowTicks.remove(player.getUniqueId());
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, config.getFarmersHandScarecrowCheckInterval());
    }

    /**
     * Check if player has moved (excluding head rotation).
     */
    private boolean hasMoved(Location last, Location current) {
        return last.getBlockX() != current.getBlockX()
                || last.getBlockY() != current.getBlockY()
                || last.getBlockZ() != current.getBlockZ();
    }

    /**
     * Check if player is standing in/near a wheat crop field.
     * Requires at least farmland-threshold farmland blocks with wheat crops nearby.
     */
    private boolean isInWheatField(Player player) {
        int threshold = config.getFarmersHandFarmlandThreshold();
        int count = 0;
        Location loc = player.getLocation();

        // Check 5x5x3 area around player
        int xzRadius = config.getFarmersHandWheatSearchRadiusXZ();
        int yRadius = config.getFarmersHandWheatSearchRadiusY();
        for (int x = -xzRadius; x <= xzRadius; x++) {
            for (int z = -xzRadius; z <= xzRadius; z++) {
                for (int y = -yRadius; y <= yRadius; y++) {
                    Block block = loc.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.WHEAT) {
                        count++;
                    }
                }
            }
        }

        return count >= threshold;
    }

    // === Toggle (Shift+Right-Click while held in hand) ===

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggle(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!InventoryUtil.isCustomItem(item, CustomItemType.FARMERS_HAND)) {
            return;
        }

        event.setCancelled(true);

        if (!player.isSneaking()) {
            return;
        }

        boolean isNowDisabled = toggleDisabled(item);
        updateLore(item);
        player.getInventory().setItemInMainHand(item);

        if (isNowDisabled) {
            player.sendActionBar(Component.text("Farmer's Hand ", NamedTextColor.YELLOW)
                    .append(Component.text("DISABLED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true)));
            // Despawn Allay
            removeAllay(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
        } else {
            player.sendActionBar(Component.text("Farmer's Hand ", NamedTextColor.YELLOW)
                    .append(Component.text("ENABLED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);
        }
    }

    // === Helmet Equip/Unequip Detection ===
    // Using a periodic check since PlayerArmorChangeEvent may not be available in all Paper versions

    /**
     * On player join, check if they're wearing the hat and spawn Allay if needed.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay 1 tick to let inventory load
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ItemStack helmet = player.getInventory().getHelmet();
            if (InventoryUtil.isCustomItem(helmet, CustomItemType.FARMERS_HAND) && !isDisabled(helmet)) {
                spawnAllay(player);
            }
        }, 20L);
    }

    /**
     * On player quit, despawn their Allay.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeAllay(event.getPlayer().getUniqueId());
        scarecrowTicks.remove(event.getPlayer().getUniqueId());
        scarecrowLastPos.remove(event.getPlayer().getUniqueId());
    }

    /**
     * On death, despawn Allay.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        removeAllay(event.getEntity().getUniqueId());
    }

    /**
     * On player movement, reset scarecrow vigil if they moved blocks.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock()) {
            scarecrowTicks.put(event.getPlayer().getUniqueId(), 0L);
        }

        // Also check helmet equip/unequip state
        Player player = event.getPlayer();
        ItemStack helmet = player.getInventory().getHelmet();
        boolean wearingHat = InventoryUtil.isCustomItem(helmet, CustomItemType.FARMERS_HAND) && !isDisabled(helmet);
        boolean hasAllay = playerAllays.containsKey(player.getUniqueId());

        if (wearingHat && !hasAllay) {
            spawnAllay(player);
        } else if (!wearingHat && hasAllay) {
            removeAllay(player.getUniqueId());
        }
    }

    // === Allay Management ===

    /**
     * Spawn an Allay companion for a player.
     */
    private void spawnAllay(Player player) {
        // Don't double-spawn
        if (playerAllays.containsKey(player.getUniqueId())) {
            return;
        }

        Location spawnLoc = player.getLocation().add(1, 1, 0);
        Allay allay = player.getWorld().spawn(spawnLoc, Allay.class, a -> {
            a.customName(Component.text("✿ Helping Hand", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            a.setCustomNameVisible(true);
            a.setPersistent(false); // Don't persist across server restarts — will respawn via join event
            a.setInvulnerable(true);
            a.setCanPickupItems(false);
            a.setCollidable(false);
            a.setSilent(false);
        });

        playerAllays.put(player.getUniqueId(), allay.getUniqueId());

        // Small spawn effect
        player.getWorld().spawnParticle(
                Particle.WAX_ON,
                spawnLoc,
                10,
                0.3, 0.3, 0.3,
                0.02
        );
        player.playSound(spawnLoc, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.8f, 1.2f);
    }

    /**
     * Remove/despawn a player's Allay.
     */
    private void removeAllay(UUID playerUuid) {
        UUID allayUuid = playerAllays.remove(playerUuid);
        if (allayUuid != null) {
            Entity entity = Bukkit.getEntity(allayUuid);
            if (entity != null && !entity.isDead()) {
                // Poof particles
                entity.getWorld().spawnParticle(
                        Particle.CLOUD,
                        entity.getLocation(),
                        8,
                        0.2, 0.2, 0.2,
                        0.02
                );
                entity.remove();
            }
        }
    }

    /**
     * Remove all tracked Allays (called on plugin disable).
     */
    public void removeAllAllays() {
        for (UUID playerUuid : new ArrayList<>(playerAllays.keySet())) {
            removeAllay(playerUuid);
        }
        if (magnetTask != null) magnetTask.cancel();
        if (allayFollowTask != null) allayFollowTask.cancel();
        if (scarecrowTask != null) scarecrowTask.cancel();
    }

    // === Stat Tracking ===

    private void updateItemsCollected(Player player, ItemStack helmet, int count) {
        if (helmet == null || !helmet.hasItemMeta()) {
            return;
        }

        ItemMeta meta = helmet.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long current = pdc.getOrDefault(PDCKeys.FARMERS_HAND_ITEMS_COLLECTED, PersistentDataType.LONG, 0L);
        pdc.set(PDCKeys.FARMERS_HAND_ITEMS_COLLECTED, PersistentDataType.LONG, current + count);
        helmet.setItemMeta(meta);

        // Update lore periodically (every 10th collection tick to reduce lore spam)
        if ((current + count) % config.getFarmersHandLoreUpdateInterval() == 0) {
            updateLore(helmet);
        }
    }

    // === PDC Helpers ===

    private boolean isDisabled(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.FARMERS_HAND_DISABLED, PersistentDataType.BOOLEAN, false);
    }

    private boolean isUpgraded(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(PDCKeys.FARMERS_HAND_UPGRADED, PersistentDataType.BOOLEAN);
    }

    private boolean toggleDisabled(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean wasDisabled = pdc.getOrDefault(PDCKeys.FARMERS_HAND_DISABLED, PersistentDataType.BOOLEAN, false);
        boolean isNowDisabled = !wasDisabled;

        if (isNowDisabled) {
            pdc.set(PDCKeys.FARMERS_HAND_DISABLED, PersistentDataType.BOOLEAN, true);
        } else {
            pdc.remove(PDCKeys.FARMERS_HAND_DISABLED);
        }

        item.setItemMeta(meta);
        return isNowDisabled;
    }

    // === Lore ===

    private void updateLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean disabled = pdc.getOrDefault(PDCKeys.FARMERS_HAND_DISABLED, PersistentDataType.BOOLEAN, false);
        boolean upgraded = pdc.has(PDCKeys.FARMERS_HAND_UPGRADED, PersistentDataType.BOOLEAN);
        long itemsCollected = pdc.getOrDefault(PDCKeys.FARMERS_HAND_ITEMS_COLLECTED, PersistentDataType.LONG, 0L);

        List<Component> lore = new ArrayList<>();

        // Upgraded header
        if (upgraded) {
            lore.add(Component.text("✦ GOLDEN HARVEST CROWN ✦", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Expanded magnet radius", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // Status
        if (disabled) {
            lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                    .append(Component.text("DISABLED", NamedTextColor.RED).decoration(TextDecoration.BOLD, true))
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                    .append(Component.text("ENABLED", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true))
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());

        // Stats
        if (itemsCollected > 0) {
            lore.add(Component.text("Items Collected: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(itemsCollected), NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // Description
        lore.add(Component.text("Summons an Allay companion", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Magnetizes nearby dropped items", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Shift+Right-Click to toggle", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    // === Easter Egg: Scarecrow's Vigil ===

    private void upgradeHand(Player player, ItemStack helmet) {
        if (helmet == null || !helmet.hasItemMeta()) {
            return;
        }

        ItemMeta meta = helmet.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Mark as upgraded
        pdc.set(PDCKeys.FARMERS_HAND_UPGRADED, PersistentDataType.BOOLEAN, true);

        // Apply gradient name
        meta.displayName(createHarvestGradientName("Golden Harvest Crown"));

        helmet.setItemMeta(meta);
        updateLore(helmet);

        // Update in inventory
        player.getInventory().setHelmet(helmet);

        // Epic effects
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.7f);

        // Particle explosion
        player.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                75,
                0.5, 0.5, 0.5,
                0.3
        );
        player.getWorld().spawnParticle(
                Particle.WAX_ON,
                player.getLocation().add(0, 1, 0),
                40,
                0.5, 0.5, 0.5,
                0.1
        );

        // Epic message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("THE SCARECROW'S VIGIL!", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" ✦", NamedTextColor.GOLD)));
        player.sendMessage(Component.text("Your patience in the wheat fields has been rewarded!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("The Farmer's Hand has become the ", NamedTextColor.GRAY)
                .append(createHarvestGradientName("Golden Harvest Crown"))
                .append(Component.text("!", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("• Expanded magnet radius", NamedTextColor.GOLD));
        player.sendMessage(Component.text("• Golden Allay particle trail", NamedTextColor.YELLOW));
        player.sendMessage(Component.empty());
    }

    /**
     * Create a straw yellow → rich gold → amber gradient.
     */
    private Component createHarvestGradientName(String text) {
        TextColor[] colors = {
                TextColor.color(255, 235, 150),  // Straw yellow
                TextColor.color(255, 215, 0),    // Gold
                TextColor.color(255, 193, 37),   // Goldenrod
                TextColor.color(218, 165, 32),   // Dark goldenrod
                TextColor.color(205, 133, 0)     // Amber
        };

        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            TextColor color = colors[i % colors.length];
            builder.append(Component.text(c, color)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
        }

        return builder.build();
    }
}
