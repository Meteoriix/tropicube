package fr.tropicube.lobby.utils;

import fr.tropicube.core.TropicubeCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Typed access point to TropicubeCore language and permissions services.
 * Fallback values ​​allow the lobby to remain explicit during shutdown
 * incomplete server, even though TropicubeCore is a required dependency.
 */
public final class LangHelper {

    private LangHelper() {}

    /**
     * Resolves {@code key} in player language and replaces settings
     * positional {@code {0}}, {@code {1}}, etc. A visible key is returned
     * if TropicubeCore is unavailable in order to make the error diagnosable.
     */
    public static String get(Player player, String key, Object... args) {
        return get(player.getUniqueId(), key, args);
    }

    public static String get(UUID uuid, String key, Object... args) {
        TropicubeCore core = getCore();
        if (core == null) return "<red>[lang:" + key + "]";
        return uuid == null
                ? core.getLanguageManager().getForLang("fr", key, args)
                : core.getLanguageManager().get(uuid, key, args);
    }

    public static Component component(Player player, String key, Object... args) {
        return component(player.getUniqueId(), key, args);
    }

    public static Component component(UUID uuid, String key, Object... args) {
        TropicubeCore core = getCore();
        if (core == null) return MiniMessage.miniMessage().deserialize("<red>[lang:" + key + "]");
        return uuid == null
                ? core.getLanguageManager().getComponentForLang("fr", key, args)
                : core.getLanguageManager().getComponent(uuid, key, args);
    }

    /**
     * Returns the MiniMessage name including prefix and grade color.
     * The raw name provided is used as a fallback if Core is unavailable.
     */
    public static String getFormattedName(UUID uuid, String fallbackName) {
        TropicubeCore core = getCore();
        if (core == null) return fallbackName;
        return core.getPermissionManager().getFormattedName(uuid, fallbackName);
    }

    /**
     * Resolves a translated YAML listing, including VIP store benefits.
     */
    public static List<String> getList(Player player, String key) {
        return getList(player.getUniqueId(), key);
    }

    public static List<String> getList(UUID uuid, String key) {
        TropicubeCore core = getCore();
        if (core == null) return Collections.emptyList();
        return core.getLanguageManager().getList(uuid, key);
    }

    /** Returns the stored language code for a player (e.g. "fr", "en"). */
    public static String getPlayerLang(UUID uuid) {
        TropicubeCore core = getCore();
        if (core == null) return "fr";
        return core.getLanguageManager().getPlayerLanguage(uuid);
    }

    private static TropicubeCore getCore() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        return plugin instanceof TropicubeCore core && core.isEnabled() ? core : null;
    }
}
