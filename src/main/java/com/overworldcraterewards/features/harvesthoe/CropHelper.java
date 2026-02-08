package com.overworldcraterewards.features.harvesthoe;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Cocoa;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Utility class for crop detection, harvesting, and replanting logic.
 */
public final class CropHelper {

    private CropHelper() {} // Utility class

    /**
     * Crops that have an age property and can be replanted.
     */
    private static final Set<Material> AGEABLE_CROPS = EnumSet.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA
    );

    /**
     * Blocks that can be harvested but don't replant (stems handle regrowth).
     */
    private static final Set<Material> HARVEST_ONLY = EnumSet.of(
            Material.MELON,
            Material.PUMPKIN
    );

    /**
     * Check if a block is a harvestable crop.
     */
    public static boolean isHarvestableCrop(Block block) {
        Material type = block.getType();
        return AGEABLE_CROPS.contains(type) ||
               HARVEST_ONLY.contains(type) ||
               type == Material.SUGAR_CANE;
    }

    /**
     * Check if a crop is fully grown and ready to harvest.
     */
    public static boolean isFullyGrown(Block block) {
        Material type = block.getType();

        // Ageable crops need to check age
        if (AGEABLE_CROPS.contains(type)) {
            if (block.getBlockData() instanceof Ageable ageable) {
                return ageable.getAge() >= ageable.getMaximumAge();
            }
            return false;
        }

        // Melons, pumpkins, and sugar cane are always "ready"
        return HARVEST_ONLY.contains(type) || type == Material.SUGAR_CANE;
    }

    /**
     * Harvest a crop block and return the drops.
     * Handles replanting for applicable crops.
     *
     * @param block The block to harvest
     * @param player The player harvesting (for drop calculation)
     * @param tool The tool being used
     * @return The drops from harvesting
     */
    public static Collection<ItemStack> harvestAndReplant(Block block, Player player, ItemStack tool) {
        Material type = block.getType();

        // Special handling for sugar cane
        if (type == Material.SUGAR_CANE) {
            return harvestSugarCane(block, tool);
        }

        // Get drops before breaking
        Collection<ItemStack> drops = block.getDrops(tool);

        // Special handling for cocoa beans (preserve facing)
        if (type == Material.COCOA) {
            harvestAndReplantCocoa(block);
            return drops;
        }

        // Melons and pumpkins - just break, don't replant
        if (HARVEST_ONLY.contains(type)) {
            block.setType(Material.AIR);
            return drops;
        }

        // Regular ageable crops - break and replant at age 0
        if (AGEABLE_CROPS.contains(type)) {
            block.setType(Material.AIR);
            block.setType(type);

            if (block.getBlockData() instanceof Ageable ageable) {
                ageable.setAge(0);
                block.setBlockData(ageable);
            }
            return drops;
        }

        return drops;
    }

    /**
     * Harvest sugar cane - only break blocks above the base.
     */
    private static Collection<ItemStack> harvestSugarCane(Block block, ItemStack tool) {
        // Find the base (lowest sugar cane block with non-sugar-cane below)
        Block current = block;
        while (current.getRelative(BlockFace.DOWN).getType() == Material.SUGAR_CANE) {
            current = current.getRelative(BlockFace.DOWN);
        }

        // The base is 'current', we want to break everything above it
        Block toBreak = current.getRelative(BlockFace.UP);

        // Count blocks to break and break them
        int blocksHarvested = 0;
        while (toBreak.getType() == Material.SUGAR_CANE) {
            blocksHarvested++;
            toBreak.setType(Material.AIR);
            toBreak = toBreak.getRelative(BlockFace.UP);
        }

        // Return consolidated drops (1 sugar cane per block broken)
        java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        if (blocksHarvested > 0) {
            drops.add(new ItemStack(Material.SUGAR_CANE, blocksHarvested));
        }
        return drops;
    }

    /**
     * Harvest and replant cocoa beans while preserving facing direction.
     */
    private static void harvestAndReplantCocoa(Block block) {
        Cocoa cocoaData = (Cocoa) block.getBlockData();
        BlockFace facing = cocoaData.getFacing();

        // Break the block
        block.setType(Material.AIR);

        // Replant with same facing
        block.setType(Material.COCOA);
        Cocoa newData = (Cocoa) block.getBlockData();
        newData.setFacing(facing);
        newData.setAge(0);
        block.setBlockData(newData);
    }

    /**
     * Check if a material is a crop type we can harvest.
     */
    public static boolean isCrop(Material material) {
        return AGEABLE_CROPS.contains(material) ||
               HARVEST_ONLY.contains(material) ||
               material == Material.SUGAR_CANE;
    }
}
