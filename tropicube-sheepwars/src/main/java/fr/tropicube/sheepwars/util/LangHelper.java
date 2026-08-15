package fr.tropicube.sheepwars.util;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.MessageStyle;
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

    public static Component component(UUID uuid, String key, Object... args) {
        TropicubeCore core = getCore();
        if (core == null) return MessageStyle.component("<sw><red>Clé de traduction indisponible : <white>" + key);
        return uuid == null
                ? core.getLanguageManager().getComponentForLang("fr", key, args)
                : core.getLanguageManager().getComponent(uuid, key, args);
    }

    private static TropicubeCore getCore() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        return plugin instanceof TropicubeCore core && core.isEnabled() ? core : null;
    }
}
