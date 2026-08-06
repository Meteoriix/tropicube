package fr.tropicube.sheepwars.util;

import fr.tropicube.core.TropicubeCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Point d'accès typé au service de traduction partagé par TropicubeCore.
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

    private static TropicubeCore getCore() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        return plugin instanceof TropicubeCore core && core.isEnabled() ? core : null;
    }
}
