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
 * Loads Paper translations, tracks each player's language and supplies placeholders shared by
 * every localized message. Supported languages are French, English, Spanish and German.
 */
public class LanguageManager {

    private final TropicubeCore plugin;
    private volatile Map<String, YamlConfiguration> languages = Map.of();
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();
    private volatile String defaultLanguage;
    private final PlaceholderValues instancePlaceholders;

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
        this.instancePlaceholders = PlaceholderValues.of("instance_name",
                resolveInstanceName(System.getenv("SERVER_NAME"), System.getenv("INSTANCE_ID"),
                        plugin.getServer().getName()));
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

        return render(uuid, msg, args);
    }

    /** Returns localized MiniMessage with safely rendered named placeholders. */
    public String get(UUID uuid, String key, PlaceholderValues placeholders) {
        return MessageStyle.miniMessage(raw(uuid, key), withGlobals(uuid, placeholders));
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
        return list.stream().map(message -> render(uuid, message)).toList();
    }

    /** Returns raw MiniMessage text for explicit language. */
    public String getForLang(String lang, String key, Object... args) {
        YamlConfiguration config = languages.getOrDefault(lang, languages.get(defaultLanguage));
        if (config == null) return key;
        String msg = config.getString(key, key);
        return render(null, msg, args);
    }

    /** Returns localized MiniMessage for an explicit language with named placeholders. */
    public String getForLang(String lang, String key, PlaceholderValues placeholders) {
        return MessageStyle.miniMessage(rawForLang(lang, key), withGlobals(null, placeholders));
    }

    /** Returns a localized Adventure component for the player. */
    public Component getComponent(UUID uuid, String key, Object... args) {
        return MessageStyle.component(get(uuid, key, args));
    }

    /** Returns a localized component with safely rendered named placeholders. */
    public Component getComponent(UUID uuid, String key, PlaceholderValues placeholders) {
        return MessageStyle.component(raw(uuid, key), withGlobals(uuid, placeholders));
    }

    /** Returns an Adventure component in an explicit language. */
    public Component getComponentForLang(String lang, String key, Object... args) {
        return MessageStyle.component(getForLang(lang, key, args));
    }

    /** Returns a localized component in an explicit language with named placeholders. */
    public Component getComponentForLang(String lang, String key, PlaceholderValues placeholders) {
        return MessageStyle.component(rawForLang(lang, key), withGlobals(null, placeholders));
    }

    private PlaceholderValues withGlobals(UUID playerId, PlaceholderValues placeholders) {
        PlaceholderValues.Builder merged = PlaceholderValues.builder();
        placeholders.asMap().forEach(merged::putValue);
        globalPlaceholders(playerId).asMap().forEach(merged::putValue);
        return merged.build();
    }

    private String render(UUID playerId, String message, Object... arguments) {
        PlaceholderValues globals = globalPlaceholders(playerId);
        if (arguments.length == 0 && !containsGlobalPlaceholder(message, globals)) return message;
        return MessageStyle.miniMessage(message, PlaceholderValues.ordered(message, globals, arguments));
    }

    private PlaceholderValues globalPlaceholders(UUID playerId) {
        PlaceholderValues.Builder globals = PlaceholderValues.builder();
        instancePlaceholders.asMap().forEach(globals::putValue);
        if (playerId != null) {
            String gradeDisplay = plugin.getPermissionManager().getCachedGradeDisplay(playerId);
            if (!gradeDisplay.isBlank()) {
                globals.putComponent("player_grade", MessageStyle.component(gradeDisplay));
            }
        }
        return globals.build();
    }

    private boolean containsGlobalPlaceholder(String message, PlaceholderValues globals) {
        return globals.asMap().keySet().stream().anyMatch(name -> message.contains("{" + name + "}"));
    }

    static String resolveInstanceName(String serverName, String instanceId, String fallbackName) {
        if (serverName != null && !serverName.isBlank()) return serverName;
        if (instanceId != null && !instanceId.isBlank()) return instanceId;
        return fallbackName;
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
