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
 * Point d'accès typé aux services de langue et de permissions de TropicubeCore.
 * Les valeurs de repli permettent au lobby de rester explicite lors d'un arrêt
 * incomplet du serveur, même si TropicubeCore est une dépendance obligatoire.
 */
public final class LangHelper {

    private LangHelper() {}

    /**
     * Résout {@code key} dans la langue du joueur et remplace les paramètres
     * positionnels {@code {0}}, {@code {1}}, etc. Une clé visible est renvoyée
     * si TropicubeCore est indisponible afin de rendre l'erreur diagnostiquable.
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
     * Renvoie le nom MiniMessage incluant préfixe et couleur du grade.
     * Le nom brut fourni est utilisé comme repli si Core est indisponible.
     */
    public static String getFormattedName(UUID uuid, String fallbackName) {
        TropicubeCore core = getCore();
        if (core == null) return fallbackName;
        return core.getPermissionManager().getFormattedName(uuid, fallbackName);
    }

    /**
     * Résout une liste YAML traduite, notamment les avantages de la boutique VIP.
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
