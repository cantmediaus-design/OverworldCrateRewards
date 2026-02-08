package com.overworldcraterewards.commands;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import com.overworldcraterewards.items.CustomItemManager;
import com.overworldcraterewards.items.CustomItemType;
import com.overworldcraterewards.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main command handler for /ocr commands.
 */
public class OCRCommand implements CommandExecutor, TabCompleter {

    private final OverworldCrateRewardsPlugin plugin;
    private final CustomItemManager itemManager;

    public OCRCommand(OverworldCrateRewardsPlugin plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "give" -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender, args);
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown command. Use /ocr help", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        // Permission check
        if (!sender.hasPermission("overworldcraterewards.give")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        // /ocr give <item> [player] [amount]
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /ocr give <item> [player] [amount]", NamedTextColor.RED));
            sender.sendMessage(Component.text("Items: " + getItemTypeNames(), NamedTextColor.GRAY));
            return true;
        }

        // Parse item type
        String itemName = args[1].toLowerCase().replace("-", "_");
        CustomItemType itemType = CustomItemType.fromId(itemName);

        if (itemType == null) {
            sender.sendMessage(Component.text("Unknown item: " + args[1], NamedTextColor.RED));
            sender.sendMessage(Component.text("Valid items: " + getItemTypeNames(), NamedTextColor.GRAY));
            return true;
        }

        // Determine target player
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Console must specify a player.", NamedTextColor.RED));
            return true;
        }

        // Parse amount
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1 || amount > 64) {
                    sender.sendMessage(Component.text("Amount must be between 1 and 64.", NamedTextColor.RED));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[3], NamedTextColor.RED));
                return true;
            }
        }

        // Create and give item
        ItemStack item = itemManager.createItem(itemType, amount);
        target.getInventory().addItem(item);

        // Notify
        String displayName = itemType.getDefaultDisplayName().replace("&", "");
        sender.sendMessage(Component.text()
                .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                .append(Component.text("Gave ", NamedTextColor.GREEN))
                .append(Component.text(amount + "x ", NamedTextColor.WHITE))
                .append(MessageUtil.colorize(itemType.getDefaultDisplayName()))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text(target.getName(), NamedTextColor.WHITE))
                .build());

        if (!sender.equals(target)) {
            target.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("You received ", NamedTextColor.GREEN))
                    .append(Component.text(amount + "x ", NamedTextColor.WHITE))
                    .append(MessageUtil.colorize(itemType.getDefaultDisplayName()))
                    .build());
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        // Permission check
        if (!sender.hasPermission("overworldcraterewards.reload")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        plugin.reload();

        sender.sendMessage(Component.text()
                .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                .append(Component.text("Configuration reloaded!", NamedTextColor.GREEN))
                .build());

        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        // Permission check - requires OP or admin permission
        if (!sender.hasPermission("overworldcraterewards.debug")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /ocr debug <action>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Actions: damage_hoe, pouch, siphon, fervor, hammer, charm, mark, melon, hand", NamedTextColor.GRAY));
            return true;
        }

        String debugAction = args[1].toLowerCase();

        return switch (debugAction) {
            case "damage_hoe" -> handleDebugDamageHoe(player);
            case "pouch" -> handleDebugPouch(player, args);
            case "siphon" -> handleDebugSiphon(player, args);
            case "fervor" -> handleDebugFervor(player, args);
            case "hammer" -> handleDebugHammer(player, args);
            case "charm" -> handleDebugCharm(player, args);
            case "mark" -> handleDebugMark(player, args);
            case "melon" -> handleDebugMelon(player, args);
            case "hand" -> handleDebugHand(player, args);
            default -> {
                sender.sendMessage(Component.text("Unknown debug action: " + debugAction, NamedTextColor.RED));
                sender.sendMessage(Component.text("Available: damage_hoe, pouch, siphon, fervor, hammer, charm, mark, melon, hand", NamedTextColor.GRAY));
                yield true;
            }
        };
    }

    private boolean handleDebugDamageHoe(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        // Check if holding a Harvest Hoe
        if (!com.overworldcraterewards.util.InventoryUtil.isCustomItem(heldItem, CustomItemType.HARVEST_HOE)) {
            player.sendMessage(Component.text("You must be holding a Harvest Hoe!", NamedTextColor.RED));
            return true;
        }

        // Check if already upgraded
        if (heldItem.getItemMeta().getPersistentDataContainer().has(
                com.overworldcraterewards.data.PDCKeys.HARVEST_HOE_UPGRADED,
                PersistentDataType.BYTE)) {
            player.sendMessage(Component.text("This Harvest Hoe is already upgraded!", NamedTextColor.YELLOW));
            return true;
        }

        // Damage the hoe to 2 durability remaining (will trigger easter egg on next use)
        if (heldItem.getItemMeta() instanceof Damageable damageable) {
            int maxDurability = heldItem.getType().getMaxDurability();
            int damageToApply = maxDurability - 2; // Leave 2 durability

            damageable.setDamage(damageToApply);
            heldItem.setItemMeta(damageable);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Harvest Hoe damaged to ", NamedTextColor.GREEN))
                    .append(Component.text("2 durability", NamedTextColor.YELLOW))
                    .append(Component.text(". Use it once to trigger the easter egg!", NamedTextColor.GREEN))
                    .build());
        }
        return true;
    }

    private boolean handleDebugPouch(Player player, String[] args) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        // Check if holding a Farmer's Pouch
        if (!com.overworldcraterewards.util.InventoryUtil.isCustomItem(heldItem, CustomItemType.FARMERS_POUCH)) {
            player.sendMessage(Component.text("You must be holding a Farmer's Pouch!", NamedTextColor.RED));
            return true;
        }

        ItemMeta meta = heldItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Get current stats
        double currentEarned = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.FARMERS_POUCH_EARNED,
                PersistentDataType.DOUBLE, 0.0);
        boolean isUpgraded = pdc.has(com.overworldcraterewards.data.PDCKeys.FARMERS_POUCH_UPGRADED,
                PersistentDataType.BOOLEAN);

        // If no sub-action, show stats
        if (args.length < 3) {
            player.sendMessage(Component.text("=== Farmer's Pouch Debug ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Earned: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatCurrency(currentEarned), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("Upgraded: ", NamedTextColor.GRAY)
                    .append(Component.text(isUpgraded ? "Yes" : "No",
                            isUpgraded ? NamedTextColor.GREEN : NamedTextColor.RED)));
            player.sendMessage(Component.text("Upgrade threshold: $1,000,000,000", NamedTextColor.GRAY));
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("Usage: /ocr debug pouch <set_near|reset>", NamedTextColor.YELLOW));
            return true;
        }

        String subAction = args[2].toLowerCase();

        if (subAction.equals("set_near")) {
            if (isUpgraded) {
                player.sendMessage(Component.text("This Farmer's Pouch is already upgraded!", NamedTextColor.YELLOW));
                return true;
            }

            // Set earned to just below threshold (999,999,000)
            double nearThreshold = 999_999_000.0;
            pdc.set(com.overworldcraterewards.data.PDCKeys.FARMERS_POUCH_EARNED,
                    PersistentDataType.DOUBLE, nearThreshold);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Farmer's Pouch set to ", NamedTextColor.GREEN))
                    .append(Component.text("$999,999,000", NamedTextColor.GOLD))
                    .append(Component.text(". Sell ~$1,000 more to trigger upgrade!", NamedTextColor.GREEN))
                    .build());
            return true;

        } else if (subAction.equals("reset")) {
            pdc.remove(com.overworldcraterewards.data.PDCKeys.FARMERS_POUCH_EARNED);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.FARMERS_POUCH_UPGRADED);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Farmer's Pouch stats reset!", NamedTextColor.GREEN))
                    .build());
            return true;
        }

        player.sendMessage(Component.text("Unknown pouch action: " + subAction, NamedTextColor.RED));
        player.sendMessage(Component.text("Available: set_near, reset", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleDebugSiphon(Player player, String[] args) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        // Check if holding a Soul Siphon
        if (!com.overworldcraterewards.util.InventoryUtil.isCustomItem(heldItem, CustomItemType.SOUL_SIPHON)) {
            player.sendMessage(Component.text("You must be holding a Soul Siphon!", NamedTextColor.RED));
            return true;
        }

        ItemMeta meta = heldItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Get current stats
        long currentKills = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.SOUL_SIPHON_KILLS,
                PersistentDataType.LONG, 0L);
        double currentEarned = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.SOUL_SIPHON_EARNED,
                PersistentDataType.DOUBLE, 0.0);
        boolean isUpgraded = pdc.has(com.overworldcraterewards.data.PDCKeys.SOUL_SIPHON_UPGRADED,
                PersistentDataType.BOOLEAN);

        // If no sub-action, show stats
        if (args.length < 3) {
            player.sendMessage(Component.text("=== Soul Siphon Debug ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Kills: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(currentKills), NamedTextColor.RED)));
            player.sendMessage(Component.text("Earned: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatCurrency(currentEarned), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("Upgraded: ", NamedTextColor.GRAY)
                    .append(Component.text(isUpgraded ? "Yes" : "No",
                            isUpgraded ? NamedTextColor.GREEN : NamedTextColor.RED)));
            player.sendMessage(Component.text("Upgrade threshold: 6,666,666 kills", NamedTextColor.GRAY));
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("Usage: /ocr debug siphon <set_near|reset>", NamedTextColor.YELLOW));
            return true;
        }

        String subAction = args[2].toLowerCase();

        if (subAction.equals("set_near")) {
            if (isUpgraded) {
                player.sendMessage(Component.text("This Soul Siphon is already upgraded!", NamedTextColor.YELLOW));
                return true;
            }

            // Set kills to just below threshold (6,666,660)
            long nearThreshold = 6_666_660L;
            pdc.set(com.overworldcraterewards.data.PDCKeys.SOUL_SIPHON_KILLS,
                    PersistentDataType.LONG, nearThreshold);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Soul Siphon set to ", NamedTextColor.GREEN))
                    .append(Component.text("6,666,660 kills", NamedTextColor.RED))
                    .append(Component.text(". Kill ~6 more mobs to trigger upgrade!", NamedTextColor.GREEN))
                    .build());
            return true;

        } else if (subAction.equals("reset")) {
            pdc.remove(com.overworldcraterewards.data.PDCKeys.SOUL_SIPHON_KILLS);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.SOUL_SIPHON_EARNED);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.SOUL_SIPHON_UPGRADED);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Soul Siphon stats reset!", NamedTextColor.GREEN))
                    .build());
            return true;
        }

        player.sendMessage(Component.text("Unknown siphon action: " + subAction, NamedTextColor.RED));
        player.sendMessage(Component.text("Available: set_near, reset", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleDebugFervor(Player player, String[] args) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        // Check if holding a Miner's Fervor
        if (!com.overworldcraterewards.util.InventoryUtil.isCustomItem(heldItem, CustomItemType.MINERS_FERVOR)) {
            player.sendMessage(Component.text("You must be holding a Miner's Fervor!", NamedTextColor.RED));
            return true;
        }

        ItemMeta meta = heldItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Get current stats from PDC
        long blocks = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.MINERS_FERVOR_BLOCKS,
                PersistentDataType.LONG, 0L);
        double earned = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.MINERS_FERVOR_EARNED,
                PersistentDataType.DOUBLE, 0.0);
        long peakStreak = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.MINERS_FERVOR_PEAK_STREAK,
                PersistentDataType.LONG, 0L);
        boolean isUpgraded = pdc.has(com.overworldcraterewards.data.PDCKeys.MINERS_FERVOR_UPGRADED,
                PersistentDataType.BOOLEAN);

        // Get current session streak from listener
        long currentStreak = plugin.getMinersFervorListener().getPlayerStreak(player);

        // If no sub-action, show stats
        if (args.length < 3) {
            player.sendMessage(Component.text("=== Miner's Fervor Debug ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Current Streak: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(currentStreak), NamedTextColor.AQUA)));
            player.sendMessage(Component.text("Peak Streak: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(peakStreak), NamedTextColor.LIGHT_PURPLE)));
            player.sendMessage(Component.text("Blocks Mined: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(blocks), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("Total Earned: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatCurrency(earned), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("Upgraded: ", NamedTextColor.GRAY)
                    .append(Component.text(isUpgraded ? "Yes" : "No",
                            isUpgraded ? NamedTextColor.GREEN : NamedTextColor.RED)));
            player.sendMessage(Component.text("Upgrade threshold: 1,000,000 peak streak", NamedTextColor.GRAY));
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("Usage: /ocr debug fervor <set_near|reset>", NamedTextColor.YELLOW));
            return true;
        }

        String subAction = args[2].toLowerCase();

        if (subAction.equals("set_near")) {
            if (isUpgraded) {
                player.sendMessage(Component.text("This Miner's Fervor is already upgraded!", NamedTextColor.YELLOW));
                return true;
            }

            // Set current streak to just below threshold (999,990)
            long nearThreshold = 999_990L;
            plugin.getMinersFervorListener().setPlayerStreak(player, nearThreshold);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Miner's Fervor current streak set to ", NamedTextColor.GREEN))
                    .append(Component.text("999,990", NamedTextColor.AQUA))
                    .append(Component.text(". Mine ~10 more blocks to trigger upgrade!", NamedTextColor.GREEN))
                    .build());
            return true;

        } else if (subAction.equals("reset")) {
            // Reset session streak
            plugin.getMinersFervorListener().setPlayerStreak(player, 0);

            // Reset PDC stats
            pdc.remove(com.overworldcraterewards.data.PDCKeys.MINERS_FERVOR_BLOCKS);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.MINERS_FERVOR_EARNED);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.MINERS_FERVOR_PEAK_STREAK);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.MINERS_FERVOR_UPGRADED);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Miner's Fervor stats reset!", NamedTextColor.GREEN))
                    .build());
            return true;
        }

        player.sendMessage(Component.text("Unknown fervor action: " + subAction, NamedTextColor.RED));
        player.sendMessage(Component.text("Available: set_near, reset", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleDebugHammer(Player player, String[] args) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (!com.overworldcraterewards.util.InventoryUtil.isCustomItem(heldItem, CustomItemType.JACKO_HAMMER)) {
            player.sendMessage(Component.text("You must be holding a Jack'o'Hammer!", NamedTextColor.RED));
            return true;
        }

        ItemMeta meta = heldItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long currentPumpkins = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.JACKO_HAMMER_PUMPKINS,
                PersistentDataType.LONG, 0L);
        boolean isUpgraded = pdc.has(com.overworldcraterewards.data.PDCKeys.JACKO_HAMMER_UPGRADED,
                PersistentDataType.BOOLEAN);

        if (args.length < 3) {
            player.sendMessage(Component.text("=== Jack'o'Hammer Debug ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Pumpkins: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(currentPumpkins), NamedTextColor.GOLD)));
            player.sendMessage(Component.text("Upgraded: ", NamedTextColor.GRAY)
                    .append(Component.text(isUpgraded ? "Yes" : "No",
                            isUpgraded ? NamedTextColor.GREEN : NamedTextColor.RED)));
            player.sendMessage(Component.text("Upgrade threshold: 500,000 pumpkins", NamedTextColor.GRAY));
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("Usage: /ocr debug hammer <set_near|reset>", NamedTextColor.YELLOW));
            return true;
        }

        String subAction = args[2].toLowerCase();

        if (subAction.equals("set_near")) {
            if (isUpgraded) {
                player.sendMessage(Component.text("This Jack'o'Hammer is already upgraded!", NamedTextColor.YELLOW));
                return true;
            }

            long nearThreshold = 499_990L;
            pdc.set(com.overworldcraterewards.data.PDCKeys.JACKO_HAMMER_PUMPKINS,
                    PersistentDataType.LONG, nearThreshold);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Jack'o'Hammer set to ", NamedTextColor.GREEN))
                    .append(Component.text("499,990 pumpkins", NamedTextColor.GOLD))
                    .append(Component.text(". Smash ~10 more pumpkins to trigger upgrade!", NamedTextColor.GREEN))
                    .build());
            return true;

        } else if (subAction.equals("reset")) {
            pdc.remove(com.overworldcraterewards.data.PDCKeys.JACKO_HAMMER_PUMPKINS);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.JACKO_HAMMER_UPGRADED);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Jack'o'Hammer stats reset!", NamedTextColor.GREEN))
                    .build());
            return true;
        }

        player.sendMessage(Component.text("Unknown hammer action: " + subAction, NamedTextColor.RED));
        player.sendMessage(Component.text("Available: set_near, reset", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleDebugCharm(Player player, String[] args) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (!com.overworldcraterewards.util.InventoryUtil.isCustomItem(heldItem, CustomItemType.ANGLERS_CHARM)) {
            player.sendMessage(Component.text("You must be holding an Angler's Charm!", NamedTextColor.RED));
            return true;
        }

        ItemMeta meta = heldItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long currentFish = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.ANGLERS_CHARM_FISH_CAUGHT,
                PersistentDataType.LONG, 0L);
        double currentEarned = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.ANGLERS_CHARM_EARNED,
                PersistentDataType.DOUBLE, 0.0);
        boolean isUpgraded = pdc.has(com.overworldcraterewards.data.PDCKeys.ANGLERS_CHARM_UPGRADED,
                PersistentDataType.BOOLEAN);

        if (args.length < 3) {
            player.sendMessage(Component.text("=== Angler's Charm Debug ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Fish Caught: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(currentFish), NamedTextColor.AQUA)));
            player.sendMessage(Component.text("Earned: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatCurrency(currentEarned), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("Upgraded: ", NamedTextColor.GRAY)
                    .append(Component.text(isUpgraded ? "Yes" : "No",
                            isUpgraded ? NamedTextColor.GREEN : NamedTextColor.RED)));
            player.sendMessage(Component.text("Upgrade threshold: 100,000 fish caught", NamedTextColor.GRAY));
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("Usage: /ocr debug charm <set_near|reset>", NamedTextColor.YELLOW));
            return true;
        }

        String subAction = args[2].toLowerCase();

        if (subAction.equals("set_near")) {
            if (isUpgraded) {
                player.sendMessage(Component.text("This Angler's Charm is already upgraded!", NamedTextColor.YELLOW));
                return true;
            }

            long nearThreshold = 99_990L;
            pdc.set(com.overworldcraterewards.data.PDCKeys.ANGLERS_CHARM_FISH_CAUGHT,
                    PersistentDataType.LONG, nearThreshold);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Angler's Charm set to ", NamedTextColor.GREEN))
                    .append(Component.text("99,990 fish", NamedTextColor.AQUA))
                    .append(Component.text(". Catch ~10 more fish to trigger upgrade!", NamedTextColor.GREEN))
                    .build());
            return true;

        } else if (subAction.equals("reset")) {
            pdc.remove(com.overworldcraterewards.data.PDCKeys.ANGLERS_CHARM_DISABLED);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.ANGLERS_CHARM_FISH_CAUGHT);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.ANGLERS_CHARM_EARNED);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.ANGLERS_CHARM_UPGRADED);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Angler's Charm stats reset!", NamedTextColor.GREEN))
                    .build());
            return true;
        }

        player.sendMessage(Component.text("Unknown charm action: " + subAction, NamedTextColor.RED));
        player.sendMessage(Component.text("Available: set_near, reset", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleDebugMark(Player player, String[] args) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (!com.overworldcraterewards.util.InventoryUtil.isCustomItem(heldItem, CustomItemType.LUMBERJACKS_MARK)) {
            player.sendMessage(Component.text("You must be holding a Lumberjack's Mark!", NamedTextColor.RED));
            return true;
        }

        ItemMeta meta = heldItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long currentLogs = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.LUMBERJACKS_MARK_BONUS_LOGS,
                PersistentDataType.LONG, 0L);
        double currentEarned = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.LUMBERJACKS_MARK_EARNED,
                PersistentDataType.DOUBLE, 0.0);
        boolean isUpgraded = pdc.has(com.overworldcraterewards.data.PDCKeys.LUMBERJACKS_MARK_UPGRADED,
                PersistentDataType.BOOLEAN);

        if (args.length < 3) {
            player.sendMessage(Component.text("=== Lumberjack's Mark Debug ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Bonus Logs: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(currentLogs), NamedTextColor.DARK_GREEN)));
            player.sendMessage(Component.text("Earned: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatCurrency(currentEarned), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("Upgraded: ", NamedTextColor.GRAY)
                    .append(Component.text(isUpgraded ? "Yes" : "No",
                            isUpgraded ? NamedTextColor.GREEN : NamedTextColor.RED)));
            player.sendMessage(Component.text("Upgrade threshold: 250,000 bonus logs", NamedTextColor.GRAY));
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("Usage: /ocr debug mark <set_near|reset>", NamedTextColor.YELLOW));
            return true;
        }

        String subAction = args[2].toLowerCase();

        if (subAction.equals("set_near")) {
            if (isUpgraded) {
                player.sendMessage(Component.text("This Lumberjack's Mark is already upgraded!", NamedTextColor.YELLOW));
                return true;
            }

            long nearThreshold = 249_990L;
            pdc.set(com.overworldcraterewards.data.PDCKeys.LUMBERJACKS_MARK_BONUS_LOGS,
                    PersistentDataType.LONG, nearThreshold);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Lumberjack's Mark set to ", NamedTextColor.GREEN))
                    .append(Component.text("249,990 bonus logs", NamedTextColor.DARK_GREEN))
                    .append(Component.text(". Break ~10 more logs to trigger upgrade!", NamedTextColor.GREEN))
                    .build());
            return true;

        } else if (subAction.equals("reset")) {
            pdc.remove(com.overworldcraterewards.data.PDCKeys.LUMBERJACKS_MARK_BONUS_LOGS);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.LUMBERJACKS_MARK_EARNED);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.LUMBERJACKS_MARK_UPGRADED);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Lumberjack's Mark stats reset!", NamedTextColor.GREEN))
                    .build());
            return true;
        }

        player.sendMessage(Component.text("Unknown mark action: " + subAction, NamedTextColor.RED));
        player.sendMessage(Component.text("Available: set_near, reset", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleDebugMelon(Player player, String[] args) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (!com.overworldcraterewards.util.InventoryUtil.isCustomItem(heldItem, CustomItemType.MELON_NATOR)) {
            player.sendMessage(Component.text("You must be holding a Melon-nator!", NamedTextColor.RED));
            return true;
        }

        ItemMeta meta = heldItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long melons = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_MELONS,
                PersistentDataType.LONG, 0L);
        long growthLevel = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_GROWTH_LEVEL,
                PersistentDataType.LONG, 0L);
        double growthChance = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_GROWTH_CHANCE,
                PersistentDataType.DOUBLE, 0.0);
        double glisteringChance = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_GLISTERING_CHANCE,
                PersistentDataType.DOUBLE, plugin.getConfigManager().getMelonNatorBaseGlisteringChance());
        boolean isUpgraded = pdc.has(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_UPGRADED,
                PersistentDataType.BOOLEAN);

        if (args.length < 3) {
            player.sendMessage(Component.text("=== Melon-nator Debug ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Melons Mined: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(melons), NamedTextColor.GREEN)));
            player.sendMessage(Component.text("Growth Level: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(growthLevel), NamedTextColor.YELLOW)));
            player.sendMessage(Component.text("Growth Chance: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.4f%%", growthChance * 100), NamedTextColor.AQUA)));
            player.sendMessage(Component.text("Glistering Chance: ", NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.4f%%", glisteringChance * 100), NamedTextColor.GOLD)));
            player.sendMessage(Component.text("Upgraded: ", NamedTextColor.GRAY)
                    .append(Component.text(isUpgraded ? "Yes" : "No",
                            isUpgraded ? NamedTextColor.GREEN : NamedTextColor.RED)));
            player.sendMessage(Component.text("Upgrade trigger: first growth event", NamedTextColor.GRAY));
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("Usage: /ocr debug melon <set_near|set_cap|reset>", NamedTextColor.YELLOW));
            return true;
        }

        String subAction = args[2].toLowerCase();

        if (subAction.equals("set_near")) {
            if (isUpgraded) {
                player.sendMessage(Component.text("This Melon-nator is already upgraded!", NamedTextColor.YELLOW));
                return true;
            }

            // Set growth chance very high so next melon triggers growth (= upgrade)
            pdc.set(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_GROWTH_CHANCE,
                    PersistentDataType.DOUBLE, 0.99);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Melon-nator growth chance set to ", NamedTextColor.GREEN))
                    .append(Component.text("99%", NamedTextColor.GOLD))
                    .append(Component.text(". Mine 1 melon to trigger first growth = upgrade!", NamedTextColor.GREEN))
                    .build());
            return true;

        } else if (subAction.equals("set_cap")) {
            // Set glistering chance to near cap to test XP bottle drops
            double nearCap = plugin.getConfigManager().getMelonNatorGlisteringCap() - 0.0001;
            pdc.set(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_GLISTERING_CHANCE,
                    PersistentDataType.DOUBLE, nearCap);
            pdc.set(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_GROWTH_CHANCE,
                    PersistentDataType.DOUBLE, 0.99);
            if (!isUpgraded) {
                pdc.set(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_UPGRADED,
                        PersistentDataType.BOOLEAN, true);
                pdc.set(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_GROWTH_LEVEL,
                        PersistentDataType.LONG, 1L);
            }
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Melon-nator set near glistering cap (", NamedTextColor.GREEN))
                    .append(Component.text(String.format("%.2f%%", nearCap * 100), NamedTextColor.GOLD))
                    .append(Component.text("). Mine melons to trigger growth → cap → XP bottles!", NamedTextColor.GREEN))
                    .build());
            return true;

        } else if (subAction.equals("reset")) {
            pdc.remove(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_MELONS);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_GROWTH_CHANCE);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_GLISTERING_CHANCE);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_GROWTH_LEVEL);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.MELON_NATOR_UPGRADED);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Melon-nator stats reset!", NamedTextColor.GREEN))
                    .build());
            return true;
        }

        player.sendMessage(Component.text("Unknown melon action: " + subAction, NamedTextColor.RED));
        player.sendMessage(Component.text("Available: set_near, set_cap, reset", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleDebugHand(Player player, String[] args) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (!com.overworldcraterewards.util.InventoryUtil.isCustomItem(heldItem, CustomItemType.FARMERS_HAND)) {
            player.sendMessage(Component.text("You must be holding a Farmer's Hand!", NamedTextColor.RED));
            return true;
        }

        ItemMeta meta = heldItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        long itemsCollected = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.FARMERS_HAND_ITEMS_COLLECTED,
                PersistentDataType.LONG, 0L);
        boolean isUpgraded = pdc.has(com.overworldcraterewards.data.PDCKeys.FARMERS_HAND_UPGRADED,
                PersistentDataType.BOOLEAN);
        boolean isDisabled = pdc.getOrDefault(com.overworldcraterewards.data.PDCKeys.FARMERS_HAND_DISABLED,
                PersistentDataType.BOOLEAN, false);

        if (args.length < 3) {
            player.sendMessage(Component.text("=== Farmer's Hand Debug ===", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Items Collected: ", NamedTextColor.GRAY)
                    .append(Component.text(MessageUtil.formatNumber(itemsCollected), NamedTextColor.YELLOW)));
            player.sendMessage(Component.text("Disabled: ", NamedTextColor.GRAY)
                    .append(Component.text(isDisabled ? "Yes" : "No",
                            isDisabled ? NamedTextColor.RED : NamedTextColor.GREEN)));
            player.sendMessage(Component.text("Upgraded: ", NamedTextColor.GRAY)
                    .append(Component.text(isUpgraded ? "Yes" : "No",
                            isUpgraded ? NamedTextColor.GREEN : NamedTextColor.RED)));
            player.sendMessage(Component.text("Upgrade: Scarecrow's Vigil (stand still 5min in wheat field)", NamedTextColor.GRAY));
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("Usage: /ocr debug hand <upgrade|reset>", NamedTextColor.YELLOW));
            return true;
        }

        String subAction = args[2].toLowerCase();

        if (subAction.equals("upgrade")) {
            if (isUpgraded) {
                player.sendMessage(Component.text("This Farmer's Hand is already upgraded!", NamedTextColor.YELLOW));
                return true;
            }

            pdc.set(com.overworldcraterewards.data.PDCKeys.FARMERS_HAND_UPGRADED,
                    PersistentDataType.BOOLEAN, true);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Farmer's Hand forcefully upgraded!", NamedTextColor.GREEN))
                    .build());
            return true;

        } else if (subAction.equals("reset")) {
            pdc.remove(com.overworldcraterewards.data.PDCKeys.FARMERS_HAND_ITEMS_COLLECTED);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.FARMERS_HAND_UPGRADED);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.FARMERS_HAND_DISABLED);
            pdc.remove(com.overworldcraterewards.data.PDCKeys.FARMERS_HAND_ALLAY_UUID);
            heldItem.setItemMeta(meta);
            player.getInventory().setItemInMainHand(heldItem);

            player.sendMessage(Component.text()
                    .append(MessageUtil.colorize(plugin.getConfigManager().getMessagePrefix()))
                    .append(Component.text("Farmer's Hand stats reset!", NamedTextColor.GREEN))
                    .build());
            return true;
        }

        player.sendMessage(Component.text("Unknown hand action: " + subAction, NamedTextColor.RED));
        player.sendMessage(Component.text("Available: upgrade, reset", NamedTextColor.GRAY));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== OverworldCrateRewards Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/ocr give <item> [player] [amount]", NamedTextColor.YELLOW)
                .append(Component.text(" - Give a custom item", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/ocr reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload configuration", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/ocr help", NamedTextColor.YELLOW)
                .append(Component.text(" - Show this help", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text("Available items: " + getItemTypeNames(), NamedTextColor.GRAY));
    }

    private String getItemTypeNames() {
        return Arrays.stream(CustomItemType.values())
                .map(CustomItemType::getId)
                .collect(Collectors.joining(", "));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Sub-commands
            List<String> subCommands = new ArrayList<>();
            if (sender.hasPermission("overworldcraterewards.give")) {
                subCommands.add("give");
            }
            if (sender.hasPermission("overworldcraterewards.reload")) {
                subCommands.add("reload");
            }
            if (sender.hasPermission("overworldcraterewards.debug")) {
                subCommands.add("debug");
            }
            subCommands.add("help");

            String partial = args[0].toLowerCase();
            completions = subCommands.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());

        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // Item types
            String partial = args[1].toLowerCase();
            completions = Arrays.stream(CustomItemType.values())
                    .map(CustomItemType::getId)
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());

        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            // Debug actions
            String partial = args[1].toLowerCase();
            completions = List.of("damage_hoe", "pouch", "siphon", "fervor", "hammer", "charm", "mark", "melon", "hand").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());

        } else if (args.length == 3 && args[0].equalsIgnoreCase("debug")) {
            // Debug sub-actions
            String debugAction = args[1].toLowerCase();
            String partial = args[2].toLowerCase();
            if (debugAction.equals("pouch") || debugAction.equals("siphon") || debugAction.equals("fervor")
                    || debugAction.equals("hammer") || debugAction.equals("charm") || debugAction.equals("mark")) {
                completions = List.of("set_near", "reset").stream()
                        .filter(s -> s.startsWith(partial))
                        .collect(Collectors.toList());
            } else if (debugAction.equals("melon")) {
                completions = List.of("set_near", "set_cap", "reset").stream()
                        .filter(s -> s.startsWith(partial))
                        .collect(Collectors.toList());
            } else if (debugAction.equals("hand")) {
                completions = List.of("upgrade", "reset").stream()
                        .filter(s -> s.startsWith(partial))
                        .collect(Collectors.toList());
            }

        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Player names
            String partial = args[2].toLowerCase();
            completions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());

        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            // Amount suggestions
            completions = List.of("1", "16", "32", "64");
        }

        return completions;
    }
}
