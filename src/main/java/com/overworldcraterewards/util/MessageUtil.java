package com.overworldcraterewards.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for message formatting.
 */
public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private MessageUtil() {} // Utility class

    /**
     * Convert legacy color codes (&a, &b, etc.) to a Component.
     * @param text The text with legacy color codes
     * @return A Component with the colors applied
     */
    public static Component colorize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SERIALIZER.deserialize(text);
    }

    /**
     * Convert a list of strings with legacy color codes to Components.
     * @param lines The lines to convert
     * @return List of Components with colors applied
     */
    public static List<Component> colorize(List<String> lines) {
        List<Component> result = new ArrayList<>();
        for (String line : lines) {
            result.add(colorize(line));
        }
        return result;
    }

    /**
     * Format a number with K/M suffixes for display.
     * @param value The number to format
     * @return Formatted string (e.g., "1.5K", "2.3M")
     */
    public static String formatNumber(long value) {
        if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    /**
     * Format a currency value.
     * @param value The value to format
     * @return Formatted string (e.g., "$1,234.56")
     */
    public static String formatCurrency(double value) {
        return String.format("$%,.2f", value);
    }

    /**
     * Format a material name for display.
     * @param materialName The material name (e.g., "WHEAT_SEEDS")
     * @return Formatted name (e.g., "Wheat Seeds")
     */
    public static String formatMaterialName(String materialName) {
        String[] parts = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }
        return result.toString();
    }
}
