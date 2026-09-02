package fr.tropicube.lobby.utils;

import fr.tropicube.language.PlaceholderValues;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.lobby.TropicubeLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    public static String get(Player player, String key, PlaceholderValues placeholders) {
        TropicubeCore core = getCore();
        if (core == null) return "<tc><red>Clé de traduction indisponible : <white>" + key;
        return core.getLanguageManager().get(player.getUniqueId(), key, placeholders);
    }

    public static String get(UUID uuid, String key, Object... args) {
        TropicubeCore core = getCore();
        if (core == null) return "<tc><red>Clé de traduction indisponible : <white>" + key;
        return uuid == null
                ? core.getLanguageManager().getForLang("fr", key, args)
                : core.getLanguageManager().get(uuid, key, args);
    }

    public static Component component(Player player, String key, Object... args) {
        return component(player.getUniqueId(), key, args);
    }

    public static Component component(Player player, String key, PlaceholderValues placeholders) {
        TropicubeCore core = getCore();
        if (core == null) return MessageStyle.component("<tc><red>Clé de traduction indisponible : <white>" + key);
        return core.getLanguageManager().getComponent(player.getUniqueId(), key, placeholders);
    }

    public static Component component(UUID uuid, String key, Object... args) {
        TropicubeCore core = getCore();
        if (core == null) return MessageStyle.component("<tc><red>Clé de traduction indisponible : <white>" + key);
        return uuid == null
                ? core.getLanguageManager().getComponentForLang("fr", key, args)
                : core.getLanguageManager().getComponent(uuid, key, args);
    }

    /** Resolves a menu title through the hot-reloadable Lobby manifest. */
    public static Component menuTitle(Player player, String menuId, Object... arguments) {
        return component(player, TropicubeLobby.getInstance().getMenuTemplates().menu(menuId).titleKey(), arguments);
    }

    /** Returns the inventory size declared by a hot-reloadable Lobby menu. */
    public static int menuSize(String menuId) {
        return TropicubeLobby.getInstance().getMenuTemplates().menu(menuId).rows() * 9;
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
     * Returns the formatted display identity, including an active nick name and
     * fake grade, without changing the real grade used for permissions.
     */
    public static String getDisplayFormattedName(UUID uuid, String fallbackName) {
        TropicubeCore core = getCore();
        if (core == null) return fallbackName;
        return core.getPermissionManager().getCachedDisplayFormattedName(uuid, fallbackName)
                .orElse(fallbackName);
    }

    /**
     * Returns the display name last applied by Core. Unlike the Paper profile
     * name, this value is updated immediately when `/nick off` restores the
     * original identity.
     */
    public static String getVisibleName(Player player) {
        return getVisibleName(player.displayName(), player.getName());
    }

    static String getVisibleName(Component displayName, String fallbackName) {
        String visibleName = PlainTextComponentSerializer.plainText().serialize(displayName);
        return visibleName.isBlank() ? fallbackName : visibleName;
    }

    /** Returns the current visible name decorated with the active display grade. */
    public static Component getFormattedNameComponent(Player player) {
        String visibleName = getVisibleName(player);
        TropicubeCore core = getCore();
        if (core == null) return Component.text(visibleName);
        return core.getPermissionManager().getCachedDisplayFormattedName(player.getUniqueId(), visibleName)
                .map(MiniMessage.miniMessage()::deserialize)
                .orElseGet(() -> Component.text(visibleName));
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

    /** Loads and formats a balance away from the Paper thread. */
    public static CompletableFuture<String> getFormattedBalanceAsync(UUID uuid) {
        TropicubeCore core = getCore();
        if (core == null) return CompletableFuture.completedFuture("—");
        return core.getEconomyManager().getBalanceAsync(uuid)
                .thenApply(core.getEconomyManager()::format);
    }

    private static TropicubeCore getCore() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        return plugin instanceof TropicubeCore core && core.isEnabled() ? core : null;
    }
}
