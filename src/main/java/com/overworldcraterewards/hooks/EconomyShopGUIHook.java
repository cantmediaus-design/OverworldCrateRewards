package com.overworldcraterewards.hooks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Hook for EconomyShopGUI integration.
 * Provides sell prices for crops from the shop plugin.
 */
public class EconomyShopGUIHook {

    private static final Logger LOGGER = Logger.getLogger("OverworldCrateRewards");
    private static boolean isAvailable = false;

    // Crops that the Farmer's Pouch will use shop prices for
    private static final Set<Material> SUPPORTED_CROPS = EnumSet.of(
            // Basic crops
            Material.WHEAT,
            Material.CARROT,
            Material.POTATO,
            Material.BEETROOT,
            Material.POISONOUS_POTATO,

            // Seeds
            Material.WHEAT_SEEDS,
            Material.BEETROOT_SEEDS,
            Material.PUMPKIN_SEEDS,
            Material.MELON_SEEDS,

            // Other farmable items
            Material.NETHER_WART,
            Material.COCOA_BEANS,
            Material.SUGAR_CANE,
            Material.MELON_SLICE,
            Material.PUMPKIN,
            Material.CACTUS,
            Material.BAMBOO,
            Material.KELP,
            Material.SEA_PICKLE,
            Material.SWEET_BERRIES,
            Material.GLOW_BERRIES,
            Material.CHORUS_FRUIT,

            // Flowers (farmable)
            Material.DANDELION,
            Material.POPPY,
            Material.BLUE_ORCHID,
            Material.ALLIUM,
            Material.AZURE_BLUET,
            Material.RED_TULIP,
            Material.ORANGE_TULIP,
            Material.WHITE_TULIP,
            Material.PINK_TULIP,
            Material.OXEYE_DAISY,
            Material.CORNFLOWER,
            Material.LILY_OF_THE_VALLEY,
            Material.SUNFLOWER,
            Material.LILAC,
            Material.ROSE_BUSH,
            Material.PEONY,
            Material.TORCHFLOWER,
            Material.PITCHER_PLANT
    );

    /**
     * Initialize the hook - check if EconomyShopGUI is available.
     */
    public static void init() {
        isAvailable = Bukkit.getPluginManager().isPluginEnabled("EconomyShopGUI")
                || Bukkit.getPluginManager().isPluginEnabled("EconomyShopGUI-Premium");

        if (isAvailable) {
            LOGGER.info("EconomyShopGUI detected! Farmer's Pouch will use shop prices for crops.");
        } else {
            LOGGER.info("EconomyShopGUI not found. Farmer's Pouch will use config prices.");
        }
    }

    /**
     * Check if EconomyShopGUI is available.
     */
    public static boolean isAvailable() {
        return isAvailable;
    }

    /**
     * Check if this material is a supported crop for shop price lookup.
     */
    public static boolean isSupportedCrop(Material material) {
        return SUPPORTED_CROPS.contains(material);
    }

    /**
     * Get the sell price for an item from EconomyShopGUI.
     *
     * @param player The player selling the item
     * @param material The material to get the price for
     * @param amount The amount being sold
     * @return The sell price, or null if not sellable or not available
     */
    public static Double getSellPrice(Player player, Material material, int amount) {
        if (!isAvailable) {
            return null;
        }

        // Only look up prices for supported crops
        if (!isSupportedCrop(material)) {
            return null;
        }

        try {
            ItemStack item = new ItemStack(material, amount);

            // Use the API to get the sell price
            Optional<me.gypopo.economyshopgui.api.objects.SellPrice> priceOptional =
                    me.gypopo.economyshopgui.api.EconomyShopGUIHook.getSellPrice(player, item);

            if (priceOptional.isPresent()) {
                me.gypopo.economyshopgui.api.objects.SellPrice sellPrice = priceOptional.get();

                // Get the prices map and find the first positive price (typically Vault)
                // The prices map is keyed by EcoType enum
                Map<?, Double> prices = sellPrice.getPrices();

                if (prices != null && !prices.isEmpty()) {
                    // Find the first valid positive price
                    for (Double price : prices.values()) {
                        if (price != null && price > 0) {
                            return price;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log once and continue with fallback
            LOGGER.warning("Failed to get EconomyShopGUI price for " + material + ": " + e.getMessage());
        }

        return null;
    }
}
