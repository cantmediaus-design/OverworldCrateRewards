package com.overworldcraterewards.items;

import org.bukkit.Material;

/**
 * Enum defining all custom item types in the plugin.
 */
public enum CustomItemType {
    HARVEST_HOE("harvest_hoe", Material.NETHERITE_HOE, "&6Harvest Hoe"),
    FARMERS_POUCH("farmers_pouch", Material.BUNDLE, "&aFarmer's Pouch"),
    SOUL_SIPHON("soul_siphon", Material.WITHER_SKELETON_SKULL, "&cSoul Siphon"),
    MINERS_FERVOR("miners_fervor", Material.NETHERITE_PICKAXE, "&bMiner's Fervor"),
    JACKO_HAMMER("jacko_hammer", Material.MACE, "&6Jack'o'Hammer"),
    ANGLERS_CHARM("anglers_charm", Material.NAUTILUS_SHELL, "&3Angler's Charm"),
    LUMBERJACKS_MARK("lumberjacks_mark", Material.SWEET_BERRIES, "&2Lumberjack's Mark"),
    MELON_NATOR("melon_nator", Material.BREEZE_ROD, "&aMelon-nator"),
    FARMERS_HAND("farmers_hand", Material.LEATHER_HELMET, "&eFarmer's Hand"),
    VACUUM_VOID_HOPPER("vacuum_void_hopper", Material.LODESTONE, "&5Vacuum Void Hopper");

    private final String id;
    private final Material material;
    private final String defaultDisplayName;

    CustomItemType(String id, Material material, String defaultDisplayName) {
        this.id = id;
        this.material = material;
        this.defaultDisplayName = defaultDisplayName;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    /**
     * Get a CustomItemType by its ID.
     * @param id The item ID
     * @return The matching type, or null if not found
     */
    public static CustomItemType fromId(String id) {
        for (CustomItemType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
