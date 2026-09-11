package fr.tropicube.sheepwars.util;

import fr.tropicube.language.PlaceholderValues;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.sheepwars.TropicubeSheepwars;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Typed access point to the translation service shared by TropicubeCore.
 */
public final class LangHelper {

    private LangHelper() {}

    public static String get(Player player, String key, Object... args) {
        return get(player.getUniqueId(), key, args);
    }

    public static String get(Player player, String key, PlaceholderValues placeholders) {
        TropicubeCore core = getCore();
        if (core == null) return "<sw><red>Clé de traduction indisponible : <white>" + key;
        return core.getLanguageManager().get(player.getUniqueId(), key, placeholders);
    }

    public static String get(CommandSender sender, String key, Object... args) {
        if (sender instanceof Player player) return get(player.getUniqueId(), key, args);
        return get((UUID) null, key, args);
    }

    public static String get(UUID uuid, String key, Object... args) {
        TropicubeCore core = getCore();
        if (core == null) return "<sw><red>Clé de traduction indisponible : <white>" + key;
        return uuid == null
                ? core.getLanguageManager().getForLang("fr", key, args)
                : core.getLanguageManager().get(uuid, key, args);
    }

    public static Component component(Player player, String key, Object... args) {
        return component(player.getUniqueId(), key, args);
    }

    public static Component component(Player player, String key, PlaceholderValues placeholders) {
        TropicubeCore core = getCore();
        if (core == null) return MessageStyle.component("<sw><red>Clé de traduction indisponible : <white>" + key);
        return core.getLanguageManager().getComponent(player.getUniqueId(), key, placeholders);
    }

    public static Component component(UUID uuid, String key, Object... args) {
        TropicubeCore core = getCore();
        if (core == null) return MessageStyle.component("<sw><red>Clé de traduction indisponible : <white>" + key);
        return uuid == null
                ? core.getLanguageManager().getComponentForLang("fr", key, args)
                : core.getLanguageManager().getComponent(uuid, key, args);
    }

    public static Component menuTitle(Player player, String menuId, Object... arguments) {
        return component(player, sheepwars().getMenuTemplates().menu(menuId).titleKey(), arguments);
    }

    public static int menuSize(String menuId) {
        return sheepwars().getMenuTemplates().menu(menuId).rows() * 9;
    }

    /** Returns the shared frame declared for this menu. */
    public static String menuFrame(String menuId) {
        return sheepwars().getMenuTemplates().menu(menuId).frame();
    }

    private static TropicubeSheepwars sheepwars() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("TropicubeSheepwars");
        if (plugin instanceof TropicubeSheepwars sheepwars && sheepwars.isEnabled()) return sheepwars;
        throw new IllegalStateException("TropicubeSheepwars indisponible");
    }

    private static TropicubeCore getCore() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        return plugin instanceof TropicubeCore core && core.isEnabled() ? core : null;
    }
}
