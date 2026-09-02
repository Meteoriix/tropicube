package fr.tropicube.core.managers;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.language.PlaceholderValues;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Language system manager.
 * Each player can choose their interface language.
 * Supported languages: fr, en, es, de
 */
public class LanguageManager {

    private final TropicubeCore plugin;
    private volatile Map<String, YamlConfiguration> languages = Map.of();
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();
    private volatile String defaultLanguage;

    public static final List<String> SUPPORTED_LANGUAGES = List.of("fr", "en", "es", "de");

    /**
     * Resolves a Minecraft client locale to a supported language for a new profile.
     * Unsupported or malformed locales deliberately fall back to English.
     */
    public static String resolveClientLanguage(String clientLocale) {
        if (clientLocale == null || clientLocale.isBlank()) return "en";
        String normalized = clientLocale.toLowerCase(Locale.ROOT).replace('-', '_');
        int separator = normalized.indexOf('_');
        String language = separator < 0 ? normalized : normalized.substring(0, separator);
        return SUPPORTED_LANGUAGES.contains(language) ? language : "en";
    }

    public LanguageManager(TropicubeCore plugin) {
        this.plugin = plugin;
        this.defaultLanguage = "fr";
    }

    public void initialize() {
        String configuredDefault = plugin.getConfig().getString("language.default", "fr");
        defaultLanguage = SUPPORTED_LANGUAGES.contains(configuredDefault) ? configuredDefault : "fr";
        languages = loadFiles();
    }

    private Map<String, YamlConfiguration> loadFiles() {
        Map<String, YamlConfiguration> loaded = new HashMap<>();
        for (String lang : SUPPORTED_LANGUAGES) {
            File file = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");
            if (file.exists()) {
                loaded.put(lang, YamlConfiguration.loadConfiguration(file));
                plugin.getLogger().info(MessageStyle.log("tc", "LANG", "<gray>Langue chargée : " + lang));
            } else {
                plugin.getLogger().warning(MessageStyle.log("tc", "LANG", "<yellow>Fichier langue manquant : " + lang + ".yml"));
            }
        }
        return Map.copyOf(loaded);
    }

    /** Returns the raw MiniMessage text in the player's language, with its settings overridden. */
    public String get(UUID uuid, String key, Object... args) {
        String lang = playerLanguages.getOrDefault(uuid, defaultLanguage);
        YamlConfiguration config = languages.getOrDefault(lang, languages.get(defaultLanguage));

        if (config == null) return "<tc><red>Langue indisponible : <white>" + key;

        String msg = config.getString(key);
        if (msg == null) {
            YamlConfiguration fallback = languages.get(defaultLanguage);
            if (fallback != null) msg = fallback.getString(key);
        }

        if (msg == null) return "<tc><red>Clé de traduction manquante : <white>" + key;

        return args.length == 0 ? msg : MessageStyle.miniMessage(msg, PlaceholderValues.ordered(msg, args));
    }

    /** Returns localized MiniMessage with safely rendered named placeholders. */
    public String get(UUID uuid, String key, PlaceholderValues placeholders) {
        return MessageStyle.miniMessage(raw(uuid, key), placeholders);
    }

    /** Returns a YAML list in the player's language, with fallback to the default language. */
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

    /** Returns raw MiniMessage text for explicit language. */
    public String getForLang(String lang, String key, Object... args) {
        YamlConfiguration config = languages.getOrDefault(lang, languages.get(defaultLanguage));
        if (config == null) return key;
        String msg = config.getString(key, key);
        return args.length == 0 ? msg : MessageStyle.miniMessage(msg, PlaceholderValues.ordered(msg, args));
    }

    /** Returns localized MiniMessage for an explicit language with named placeholders. */
    public String getForLang(String lang, String key, PlaceholderValues placeholders) {
        return MessageStyle.miniMessage(rawForLang(lang, key), placeholders);
    }

    /** Returns a localized Adventure component for the player. */
    public Component getComponent(UUID uuid, String key, Object... args) {
        return MessageStyle.component(get(uuid, key, args));
    }

    /** Returns a localized component with safely rendered named placeholders. */
    public Component getComponent(UUID uuid, String key, PlaceholderValues placeholders) {
        return MessageStyle.component(raw(uuid, key), placeholders);
    }

    /** Returns an Adventure component in an explicit language. */
    public Component getComponentForLang(String lang, String key, Object... args) {
        return MessageStyle.component(getForLang(lang, key, args));
    }

    /** Returns a localized component in an explicit language with named placeholders. */
    public Component getComponentForLang(String lang, String key, PlaceholderValues placeholders) {
        return MessageStyle.component(rawForLang(lang, key), placeholders);
    }

    private String raw(UUID uuid, String key) {
        return rawForLang(playerLanguages.getOrDefault(uuid, defaultLanguage), key);
    }

    private String rawForLang(String lang, String key) {
        YamlConfiguration config = languages.getOrDefault(lang, languages.get(defaultLanguage));
        if (config == null) return "<tc><red>Langue indisponible : <white>" + key;
        String message = config.getString(key);
        if (message == null) {
            YamlConfiguration fallback = languages.get(defaultLanguage);
            if (fallback != null) message = fallback.getString(key);
        }
        return message == null ? "<tc><red>Clé de traduction manquante : <white>" + key : message;
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
            case "fr" -> "<blue>■<white>■<red>■ <dark_gray>FR <white>Français";
            case "en" -> "<dark_blue>■<white>■<red>■ <dark_gray>EN <white>English";
            case "es" -> "<red>■<yellow>■<red>■ <dark_gray>ES <white>Español";
            case "de" -> "<dark_gray>■<red>■<yellow>■ <dark_gray>DE <white>Deutsch";
            default -> code;
        };
    }

    public void reload() {
        initialize();
    }

    /** Reloads only language files and atomically publishes the new immutable catalog. */
    public void reloadFiles() {
        languages = loadFiles();
    }

    public Map<String, YamlConfiguration> getLanguages() { return Collections.unmodifiableMap(languages); }
}
