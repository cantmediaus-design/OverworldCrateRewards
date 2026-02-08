package com.overworldcraterewards.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Item;

import java.util.logging.Logger;

/**
 * Hook for RoseStacker integration.
 * Allows Soul Siphon to properly handle stacked mob kills
 * and Farmer's Pouch to read real stacked item amounts.
 */
public class RoseStackerHook {

    private static final Logger LOGGER = Logger.getLogger("OverworldCrateRewards");
    private static boolean isAvailable = false;

    /**
     * Initialize the hook - check if RoseStacker is available.
     */
    public static void init() {
        isAvailable = Bukkit.getPluginManager().isPluginEnabled("RoseStacker");

        if (isAvailable) {
            LOGGER.info("RoseStacker detected! Soul Siphon will multiply bonuses for stacked mob kills.");
        }
    }

    /**
     * Check if RoseStacker is available.
     */
    public static boolean isAvailable() {
        return isAvailable;
    }

    /**
     * Get the real stacked amount of a dropped Item entity.
     * If RoseStacker is available and the item is stacked, returns the true stack size.
     * Otherwise falls back to the vanilla Bukkit amount.
     */
    public static int getStackedItemAmount(Item itemEntity) {
        if (!isAvailable) {
            return itemEntity.getItemStack().getAmount();
        }
        try {
            dev.rosewood.rosestacker.api.RoseStackerAPI api =
                    dev.rosewood.rosestacker.api.RoseStackerAPI.getInstance();
            if (api.isItemStacked(itemEntity)) {
                dev.rosewood.rosestacker.stack.StackedItem stacked = api.getStackedItem(itemEntity);
                if (stacked != null) {
                    return stacked.getStackSize();
                }
            }
        } catch (Exception e) {
            // RoseStacker API error, fall back to vanilla
        }
        return itemEntity.getItemStack().getAmount();
    }
}
