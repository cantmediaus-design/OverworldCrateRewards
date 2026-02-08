package com.overworldcraterewards.features.anglerscharm;

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
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles the Angler's Charm accessory that auto-sells fish on catch.
 * Fish catches are sold instantly and never enter the player's inventory.
 * Can be toggled on/off by shift+right-clicking while holding it.
 *
 * Easter Egg: At 100,000 fish caught, upgrades to "Poseidon's Favor" with:
 * - 20% double catch chance (up from 10%)
 * - +10% sell price on all fish
 * - 3% chance for prismarine shard bonus drop
 */
public class AnglersCharmListener implements Listener {

    private final OverworldCrateRewardsPlugin plugin;
    private final ConfigManager config;
    private final EconomyManager economy;

    // Fish materials that trigger auto-sell
    private static final Set<Material> FISH_MATERIALS = Set.of(
            Material.COD,
            Material.SALMON,
            Material.TROPICAL_FISH,
            Material.PUFFERFISH
    );

    public AnglersCharmListener(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.economy = plugin.getEconomyManager();
    }

    /**
     * Handle right-click interactions on the Angler's Charm.
     * Cancels any default interaction and handles shift+right-click to toggle on/off.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggle(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!InventoryUtil.isCustomItem(item, CustomItemType.ANGLERS_CHARM)) {
            return;
        }

        // Cancel to prevent any default interaction
        event.setCancelled(true);

        // Only toggle if sneaking
        if (!player.isSneaking()) {
            return;
        }

        boolean isNowDisabled = toggleDisabled(item);
        updateCharmLore(item);
        player.getInventory().setItemInMainHand(item);

        if (isNowDisabled) {
            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(config.getMessagePrefix()))
                    .append(Component.text("Angler's Charm ", NamedTextColor.RED))
                    .append(Component.text("DISABLED", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(" - Fish will go to inventory", NamedTextColor.GRAY))
                    .build());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        } else {
            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(config.getMessagePrefix()))
                    .append(Component.text("Angler's Charm ", NamedTextColor.GREEN))
                    .append(Component.text("ENABLED", NamedTextColor.DARK_GREEN).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(" - Fish will auto-sell", NamedTextColor.GRAY))
                    .build());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
        }
    }

    /**
     * Handle fish catches: auto-sell fish with optional double catch.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        // Check if the caught entity is an item
        if (!(event.getCaught() instanceof Item caughtItem)) {
            return;
        }

        ItemStack caughtStack = caughtItem.getItemStack();
        Material fishType = caughtStack.getType();

        // Only process fish materials
        if (!FISH_MATERIALS.contains(fishType)) {
            return;
        }

        Player player = event.getPlayer();

        // Find Angler's Charm in inventory
        ItemStack charm = InventoryUtil.findItemInInventory(player, CustomItemType.ANGLERS_CHARM);
        if (charm == null) {
            return;
        }

        // Check if disabled
        if (isDisabled(charm)) {
            return;
        }

        // Get fish price
        Double price = config.getFishPrice(fishType);
        if (price == null) {
            return;
        }

        int amount = caughtStack.getAmount();
        boolean upgraded = isUpgradedCharm(charm);

        // Roll for double catch
        double doubleCatchChance = upgraded ? config.getAnglersCharmUpgradedDoubleCatchChance() : config.getAnglersCharmDoubleCatchChance();
        boolean doubleCatch = Math.random() < doubleCatchChance;

        // Calculate final price
        double totalPrice = price * amount;
        if (doubleCatch) {
            totalPrice *= 2;
        }
        if (upgraded) {
            totalPrice *= config.getAnglersCharmUpgradedSellMultiplier();
        }

        // Remove the caught item entity so it never enters inventory
        caughtItem.remove();

        // Deposit money
        economy.deposit(player, totalPrice);

        // Upgraded: 3% prismarine shard bonus
        if (upgraded && Math.random() < config.getAnglersCharmPrismarineShardChance()) {
            player.getInventory().addItem(new ItemStack(Material.PRISMARINE_SHARD, 1));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.8f);
            player.getWorld().spawnParticle(
                    Particle.ENCHANT,
                    player.getLocation().add(0, 1.5, 0),
                    15,
                    0.3, 0.3, 0.3,
                    0.5
            );
        }

        // Update stats
        int fishCount = doubleCatch ? amount * 2 : amount;
        updateCharmStats(player, charm, fishCount, totalPrice);

        // Action bar notification
        String fishName = MessageUtil.formatMaterialName(fishType.name());
        if (doubleCatch) {
            player.sendActionBar(Component.text()
                    .append(Component.text("+" + MessageUtil.formatCurrency(totalPrice), NamedTextColor.GOLD))
                    .append(Component.text(" (" + amount + "x " + fishName + ") ", NamedTextColor.GRAY))
                    .append(Component.text("DOUBLE!", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true))
                    .build());

            player.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    player.getLocation().add(0, 1, 0),
                    10,
                    0.3, 0.3, 0.3,
                    0
            );
            player.getWorld().spawnParticle(
                    Particle.SPLASH,
                    player.getLocation().add(0, 1, 0),
                    20,
                    0.3, 0.3, 0.3,
                    0.1
            );
            player.getWorld().spawnParticle(
                    Particle.BUBBLE_POP,
                    player.getLocation().add(0, 1, 0),
                    10,
                    0.2, 0.2, 0.2,
                    0.02
            );
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        } else {
            player.sendActionBar(Component.text()
                    .append(Component.text("+" + MessageUtil.formatCurrency(totalPrice), NamedTextColor.DARK_AQUA))
                    .append(Component.text(" (" + amount + "x " + fishName + ")", NamedTextColor.GRAY))
                    .build());
        }
    }

    /**
     * Toggle the disabled state of an Angler's Charm.
     * @return true if now disabled, false if now enabled
     */
    private boolean toggleDisabled(ItemStack charm) {
        ItemMeta meta = charm.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean wasDisabled = pdc.getOrDefault(PDCKeys.ANGLERS_CHARM_DISABLED, PersistentDataType.BOOLEAN, false);
        boolean isNowDisabled = !wasDisabled;

        if (isNowDisabled) {
            pdc.set(PDCKeys.ANGLERS_CHARM_DISABLED, PersistentDataType.BOOLEAN, true);
        } else {
            pdc.remove(PDCKeys.ANGLERS_CHARM_DISABLED);
        }

        charm.setItemMeta(meta);
        return isNowDisabled;
    }

    private boolean isDisabled(ItemStack charm) {
        if (charm == null || !charm.hasItemMeta()) {
            return false;
        }
        return charm.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.ANGLERS_CHARM_DISABLED, PersistentDataType.BOOLEAN, false);
    }

    private boolean isUpgradedCharm(ItemStack charm) {
        if (charm == null || !charm.hasItemMeta()) {
            return false;
        }
        return charm.getItemMeta().getPersistentDataContainer()
                .has(PDCKeys.ANGLERS_CHARM_UPGRADED, PersistentDataType.BOOLEAN);
    }

    /**
     * Update charm stats in PDC and check for upgrade threshold.
     */
    private void updateCharmStats(Player player, ItemStack charm, int fishCaught, double earned) {
        if (charm == null || !charm.hasItemMeta()) {
            return;
        }

        ItemMeta meta = charm.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long currentFish = pdc.getOrDefault(PDCKeys.ANGLERS_CHARM_FISH_CAUGHT, PersistentDataType.LONG, 0L);
        double currentEarned = pdc.getOrDefault(PDCKeys.ANGLERS_CHARM_EARNED, PersistentDataType.DOUBLE, 0.0);

        long newFish = currentFish + fishCaught;
        double newEarned = currentEarned + earned;

        pdc.set(PDCKeys.ANGLERS_CHARM_FISH_CAUGHT, PersistentDataType.LONG, newFish);
        pdc.set(PDCKeys.ANGLERS_CHARM_EARNED, PersistentDataType.DOUBLE, newEarned);
        charm.setItemMeta(meta);

        // Check for upgrade
        if (!isUpgradedCharm(charm) && newFish >= config.getAnglersCharmUpgradeThreshold()) {
            upgradeCharm(charm, player);
        }

        // Update lore
        updateCharmLore(charm);

        // Update item in inventory
        updateCharmInInventory(player, charm);
    }

    /**
     * Find and update the charm in the player's inventory.
     */
    private void updateCharmInInventory(Player player, ItemStack updatedCharm) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (InventoryUtil.isCustomItem(item, CustomItemType.ANGLERS_CHARM)) {
                player.getInventory().setItem(i, updatedCharm);
                return;
            }
        }

        if (InventoryUtil.isCustomItem(player.getInventory().getItemInOffHand(), CustomItemType.ANGLERS_CHARM)) {
            player.getInventory().setItemInOffHand(updatedCharm);
        }
    }

    /**
     * Update the Angler's Charm lore to show stats and status.
     */
    private void updateCharmLore(ItemStack charm) {
        if (charm == null || !charm.hasItemMeta()) {
            return;
        }

        ItemMeta meta = charm.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        boolean disabled = pdc.getOrDefault(PDCKeys.ANGLERS_CHARM_DISABLED, PersistentDataType.BOOLEAN, false);
        boolean upgraded = pdc.has(PDCKeys.ANGLERS_CHARM_UPGRADED, PersistentDataType.BOOLEAN);
        long fishCaught = pdc.getOrDefault(PDCKeys.ANGLERS_CHARM_FISH_CAUGHT, PersistentDataType.LONG, 0L);
        double earned = pdc.getOrDefault(PDCKeys.ANGLERS_CHARM_EARNED, PersistentDataType.DOUBLE, 0.0);

        List<Component> lore = new ArrayList<>();

        // Show upgraded status
        if (upgraded) {
            lore.add(Component.text("\u2726 POSEIDON'S EDITION \u2726", NamedTextColor.DARK_AQUA)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("+10% price | 20% double | 3% prismarine", NamedTextColor.AQUA)
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
        if (fishCaught > 0) {
            lore.add(Component.empty());
            lore.add(Component.text("Fish Caught: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(fishCaught), NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Total Earned: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatCurrency(earned), NamedTextColor.GOLD))
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());

        // Description
        if (upgraded) {
            lore.add(Component.text("Auto-sells fish on catch (+10%)", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Auto-sells fish on catch", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Shift+Right-Click to toggle", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        charm.setItemMeta(meta);
    }

    /**
     * Upgrade the Angler's Charm to Poseidon's Favor.
     */
    private void upgradeCharm(ItemStack charm, Player player) {
        if (charm == null || !charm.hasItemMeta()) {
            return;
        }

        ItemMeta meta = charm.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        pdc.set(PDCKeys.ANGLERS_CHARM_UPGRADED, PersistentDataType.BOOLEAN, true);
        meta.displayName(createAquaGradientName("Poseidon's Favor"));

        charm.setItemMeta(meta);

        // Epic effects
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_PLAY, 1.0f, 1.0f);

        // Particle explosion
        player.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                75,
                0.5, 0.5, 0.5,
                0.3
        );
        player.getWorld().spawnParticle(
                Particle.BUBBLE_COLUMN_UP,
                player.getLocation().add(0, 1, 0),
                50,
                1.0, 0.5, 1.0,
                0
        );
        player.getWorld().spawnParticle(
                Particle.DOLPHIN,
                player.getLocation().add(0, 1, 0),
                30,
                0.5, 0.5, 0.5,
                0
        );

        // Epic message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("\u2726 ", NamedTextColor.DARK_AQUA)
                .append(Component.text("OCEAN'S BLESSING!", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" \u2726", NamedTextColor.DARK_AQUA)));
        player.sendMessage(Component.text("The sea has recognized your devotion to the tides!", NamedTextColor.GREEN));
        player.sendMessage(Component.text("The Angler's Charm has become ", NamedTextColor.GRAY)
                .append(createAquaGradientName("Poseidon's Favor"))
                .append(Component.text("!", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("\u2022 20% double catch chance (up from 10%)", NamedTextColor.AQUA));
        player.sendMessage(Component.text("\u2022 +10% sell price on all fish", NamedTextColor.DARK_AQUA));
        player.sendMessage(Component.text("\u2022 3% chance for prismarine shard bonus", NamedTextColor.AQUA));
        player.sendMessage(Component.empty());
    }

    /**
     * Create an aqua-to-dark-aqua gradient name component.
     */
    private Component createAquaGradientName(String text) {
        TextColor[] aquaColors = {
                TextColor.color(0, 255, 255),    // Aqua / Cyan
                TextColor.color(0, 220, 220),    // Slightly darker
                TextColor.color(0, 190, 200),    // Medium
                TextColor.color(0, 150, 170),    // Dark Aqua
                TextColor.color(0, 128, 128)     // Teal / Dark Aqua
        };

        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            TextColor color = aquaColors[i % aquaColors.length];
            builder.append(Component.text(c, color)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
        }

        return builder.build();
    }
}
