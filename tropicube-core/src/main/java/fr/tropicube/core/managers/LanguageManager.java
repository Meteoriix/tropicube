package fr.tropicube.core.managers;

import fr.tropicube.core.TropicubeCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du système de langues.
 * Chaque joueur peut choisir sa langue d'interface.
 * Langues supportées : fr, en, es, de
 */
public class LanguageManager {

    private final TropicubeCore plugin;
    private final Map<String, YamlConfiguration> languages = new HashMap<>();
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();
    private volatile String defaultLanguage;

    public static final List<String> SUPPORTED_LANGUAGES = List.of("fr", "en", "es", "de");

    public LanguageManager(TropicubeCore plugin) {
        this.plugin = plugin;
        this.defaultLanguage = "fr";
    }

    public void initialize() {
        String configuredDefault = plugin.getConfig().getString("language.default", "fr");
        defaultLanguage = SUPPORTED_LANGUAGES.contains(configuredDefault) ? configuredDefault : "fr";
        for (String lang : SUPPORTED_LANGUAGES) {
            File file = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");
            if (file.exists()) {
                languages.put(lang, YamlConfiguration.loadConfiguration(file));
                plugin.getLogger().info("[Tropicube-Lang] Langue chargée : " + lang);
            } else {
                plugin.getLogger().warning("[Tropicube-Lang] Fichier langue manquant : " + lang + ".yml");
            }
        }
    }

    /** Retourne le texte MiniMessage brut dans la langue du joueur, avec ses paramètres remplacés. */
    public String get(UUID uuid, String key, Object... args) {
        String lang = playerLanguages.getOrDefault(uuid, defaultLanguage);
        YamlConfiguration config = languages.getOrDefault(lang, languages.get(defaultLanguage));

        if (config == null) return "<red>[Missing lang: " + key + "]";

        String msg = config.getString(key);
        if (msg == null) {
            YamlConfiguration fallback = languages.get(defaultLanguage);
            if (fallback != null) msg = fallback.getString(key);
        }

        if (msg == null) return "<red>[Missing key: " + key + "]";

        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return msg;
    }

    /** Retourne une liste YAML dans la langue du joueur, avec repli sur la langue par défaut. */
    public List<String> getList(UUID uuid, String key) {
        String lang = playerLanguages.getOrDefault(uuid, defaultLanguage);
        YamlConfiguration config = languages.getOrDefault(lang, languages.get(defaultLanguage));
        if (config == null) return Collections.emptyList();
        List<String> list = config.getStringList(key);
        if (list.isEmpty()) {
            YamlConfiguration fallback = languages.get(defaultLanguage);
            if (fallback != null) list = fallback.getStringList(key);
        }
        return list;
    }

    /** Retourne le texte MiniMessage brut pour une langue explicite. */
    public String getForLang(String lang, String key, Object... args) {
        YamlConfiguration config = languages.getOrDefault(lang, languages.get(defaultLanguage));
        if (config == null) return key;
        String msg = config.getString(key, key);
        for (int i = 0; i < args.length; i++) {
            msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return msg;
    }

    /** Retourne un composant Adventure localisé pour le joueur. */
    public Component getComponent(UUID uuid, String key, Object... args) {
        return MiniMessage.miniMessage().deserialize(get(uuid, key, args));
    }

    /** Retourne un composant Adventure dans une langue explicite. */
    public Component getComponentForLang(String lang, String key, Object... args) {
        return MiniMessage.miniMessage().deserialize(getForLang(lang, key, args));
    }

    public void setPlayerLanguage(UUID uuid, String lang, boolean save) {
        if (!SUPPORTED_LANGUAGES.contains(lang)) return;
        playerLanguages.put(uuid, lang);
        plugin.getRedisManager().setPlayerLanguage(uuid.toString(), lang);
        plugin.getRedisManager().publishPlayerEvent("LANG_CHANGED", uuid + ":" + lang);
        if (save) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getDatabaseManager().executeUpdate(
                        "UPDATE tropicube_players SET language = ? WHERE uuid = ?",
                        lang, uuid.toString())
            );
        }
    }

    public void loadPlayerLanguage(UUID uuid, String lang) {
        String l = SUPPORTED_LANGUAGES.contains(lang) ? lang : defaultLanguage;
        playerLanguages.put(uuid, l);
        plugin.getRedisManager().setPlayerLanguage(uuid.toString(), l);
        plugin.getRedisManager().publishPlayerEvent("LANG_CHANGED", uuid + ":" + l);
    }

    public void unloadPlayer(UUID uuid) {
        playerLanguages.remove(uuid);
    }

    public String getPlayerLanguage(UUID uuid) {
        return playerLanguages.getOrDefault(uuid, defaultLanguage);
    }

    public String getLanguageDisplayName(String code) {
        return switch (code) {
            case "fr" -> "<gray>🇫🇷 <white>Français";
            case "en" -> "<gray>🇬🇧 <white>English";
            case "es" -> "<gray>🇪🇸 <white>Español";
            case "de" -> "<gray>🇩🇪 <white>Deutsch";
            default -> code;
        };
    }

    public void reload() {
        languages.clear();
        initialize();
    }

    public Map<String, YamlConfiguration> getLanguages() { return Collections.unmodifiableMap(languages); }
}
