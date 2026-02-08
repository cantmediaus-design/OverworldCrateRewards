package com.overworldcraterewards.data;

import com.overworldcraterewards.OverworldCrateRewardsPlugin;
import org.bukkit.NamespacedKey;

/**
 * Central registry for all PersistentDataContainer keys used by the plugin.
 */
public final class PDCKeys {

    private PDCKeys() {} // Utility class

    private static NamespacedKey key(String name) {
        return new NamespacedKey(OverworldCrateRewardsPlugin.getInstance(), name);
    }

    // Item type identification
    public static final NamespacedKey ITEM_TYPE = key("item_type");

    // Harvest Hoe innate tracker (uses 10x milestone requirements)
    public static final NamespacedKey HARVEST_HOE_CROPS = key("harvest_hoe_crops");

    // Harvest Hoe upgraded status (easter egg - 5x5 base, unbreakable)
    public static final NamespacedKey HARVEST_HOE_UPGRADED = key("harvest_hoe_upgraded");

    // Farmer's Pouch disabled state (toggle with shift+right-click)
    public static final NamespacedKey FARMERS_POUCH_DISABLED = key("farmers_pouch_disabled");

    // Soul Siphon tracking (easter egg - upgrades at 6,666,666 kills)
    public static final NamespacedKey SOUL_SIPHON_KILLS = key("soul_siphon_kills");
    public static final NamespacedKey SOUL_SIPHON_EARNED = key("soul_siphon_earned");
    public static final NamespacedKey SOUL_SIPHON_UPGRADED = key("soul_siphon_upgraded");

    // Farmer's Pouch tracking (easter egg - upgrades at $1B earned)
    public static final NamespacedKey FARMERS_POUCH_EARNED = key("farmers_pouch_earned");
    public static final NamespacedKey FARMERS_POUCH_UPGRADED = key("farmers_pouch_upgraded");

    // Miner's Fervor tracking (easter egg - upgrades at 1M peak streak)
    public static final NamespacedKey MINERS_FERVOR_BLOCKS = key("miners_fervor_blocks");
    public static final NamespacedKey MINERS_FERVOR_EARNED = key("miners_fervor_earned");
    public static final NamespacedKey MINERS_FERVOR_PEAK_STREAK = key("miners_fervor_peak_streak");
    public static final NamespacedKey MINERS_FERVOR_UPGRADED = key("miners_fervor_upgraded");

    // Jack'o'Hammer tracking (easter egg - upgrades at 500,000 pumpkins)
    public static final NamespacedKey JACKO_HAMMER_PUMPKINS = key("jacko_hammer_pumpkins");
    public static final NamespacedKey JACKO_HAMMER_UPGRADED = key("jacko_hammer_upgraded");

    // Angler's Charm tracking (easter egg - upgrades at 100,000 fish caught)
    public static final NamespacedKey ANGLERS_CHARM_DISABLED = key("anglers_charm_disabled");
    public static final NamespacedKey ANGLERS_CHARM_FISH_CAUGHT = key("anglers_charm_fish_caught");
    public static final NamespacedKey ANGLERS_CHARM_EARNED = key("anglers_charm_earned");
    public static final NamespacedKey ANGLERS_CHARM_UPGRADED = key("anglers_charm_upgraded");

    // Lumberjack's Mark tracking (easter egg - upgrades at 250,000 bonus logs)
    public static final NamespacedKey LUMBERJACKS_MARK_BONUS_LOGS = key("lumberjacks_mark_bonus_logs");
    public static final NamespacedKey LUMBERJACKS_MARK_EARNED = key("lumberjacks_mark_earned");
    public static final NamespacedKey LUMBERJACKS_MARK_UPGRADED = key("lumberjacks_mark_upgraded");

    // Melon-nator tracking (easter egg - upgrades on first growth trigger)
    public static final NamespacedKey MELON_NATOR_MELONS = key("melon_nator_melons");
    public static final NamespacedKey MELON_NATOR_GROWTH_CHANCE = key("melon_nator_growth_chance");
    public static final NamespacedKey MELON_NATOR_GLISTERING_CHANCE = key("melon_nator_glistering_chance");
    public static final NamespacedKey MELON_NATOR_GROWTH_LEVEL = key("melon_nator_growth_level");
    public static final NamespacedKey MELON_NATOR_UPGRADED = key("melon_nator_upgraded");

    // Farmer's Hand tracking (easter egg - Scarecrow's Vigil)
    public static final NamespacedKey FARMERS_HAND_ITEMS_COLLECTED = key("farmers_hand_items_collected");
    public static final NamespacedKey FARMERS_HAND_UPGRADED = key("farmers_hand_upgraded");
    public static final NamespacedKey FARMERS_HAND_DISABLED = key("farmers_hand_disabled");
    public static final NamespacedKey FARMERS_HAND_ALLAY_UUID = key("farmers_hand_allay_uuid");

    // Vacuum Void Hopper
    public static final NamespacedKey VACUUM_HOPPER_FILTER = key("vacuum_hopper_filter");
    public static final NamespacedKey VACUUM_HOPPER_LINKS = key("vacuum_hopper_links");
    public static final NamespacedKey VACUUM_HOPPER_OWNER = key("vacuum_hopper_owner");
}
