package com.willowdale.talky.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private static boolean papiHooked = false;

    private TextUtil() {
    }

    public static void setPapiHooked(boolean hooked) {
        papiHooked = hooked;
    }

    public static Component parse(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        String formatted = message;
        if (player != null) {
            formatted = formatted.replace("%player%", player.getName())
                                 .replace("%player_name%", player.getName());

            if (papiHooked && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    formatted = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, formatted);
                } catch (Throwable ignored) {
                }
            }
        }

        // Translate &#RRGGBB format to MiniMessage <#RRGGBB>
        Matcher matcher = HEX_PATTERN.matcher(formatted);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "<#" + matcher.group(1) + ">");
        }
        matcher.appendTail(buffer);
        formatted = buffer.toString().replace("<newline>", "\n").replace("<br>", "\n");

        // If message contains legacy formatting (& or §), convert to Component
        if (formatted.contains("&") || formatted.contains("§")) {
            // If it also contains MiniMessage tags like <...>
            if (formatted.contains("<") && formatted.contains(">")) {
                try {
                    // Try MiniMessage first
                    return MINI_MESSAGE.deserialize(formatted);
                } catch (Exception ignored) {
                }
            }
            if (formatted.contains("&")) {
                return AMPERSAND_SERIALIZER.deserialize(formatted);
            }
            return SECTION_SERIALIZER.deserialize(formatted);
        }

        try {
            return MINI_MESSAGE.deserialize(formatted);
        } catch (Exception e) {
            return Component.text(formatted);
        }
    }

    public static void sendMessage(Player player, String message) {
        if (player == null || message == null) {
            return;
        }
        player.sendMessage(parse(player, message));
    }
}
