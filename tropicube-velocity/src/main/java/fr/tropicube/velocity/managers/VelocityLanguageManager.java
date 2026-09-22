package fr.tropicube.velocity.managers;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.language.PlaceholderValues;
import fr.tropicube.velocity.util.MessageStyle;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Loads proxy translations and resolves player language, current-instance and grade placeholders. */
public class VelocityLanguageManager {

    public static final List<String> SUPPORTED = List.of("fr", "en", "es", "de");
    private static final String DEFAULT_GRADE_DISPLAY = "<gray><bold>JOUEUR</bold></gray>";

    private volatile Map<String, ConfigurationNode> languages = Map.of();
    private final Map<UUID, String> playerLangs = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerGradeDisplays = new ConcurrentHashMap<>();
    private final RedisManager redisManager;
    private final ProxyServer proxyServer;
    private final Path dataDir;
    private final Logger logger;
    private final String defaultLang;

    public VelocityLanguageManager(Path dataDir, RedisManager redisManager, Logger logger, String defaultLang,
                                   ProxyServer proxyServer) {
        this.dataDir = dataDir;
        this.redisManager = redisManager;
        this.logger = logger;
        this.proxyServer = proxyServer;
        this.defaultLang = SUPPORTED.contains(defaultLang) ? defaultLang : "fr";
        loadLanguages();
        subscribeToLangChanges();
    }

    private void loadLanguages() {
        try {
            languages = readLanguages();
        } catch (IOException failure) {
            logger.warn(MessageStyle.log("LANG", "<yellow>Catalogue de langues inchangé : {}"), failure.getMessage());
        }
    }

    private Map<String, ConfigurationNode> readLanguages() throws IOException {
        Path langDir = dataDir.resolve("languages");
        Map<String, ConfigurationNode> loaded = new HashMap<>();
        for (String lang : SUPPORTED) {
            Path file = langDir.resolve(lang + ".yml");
            if (!Files.exists(file)) {
                try {
                    Files.createDirectories(langDir);
                    try (InputStream in = getClass().getResourceAsStream("/languages/" + lang + ".yml")) {
                        if (in != null) Files.copy(in, file);
                    }
                } catch (IOException failure) {
                    throw new IOException("Impossible de copier " + lang + ".yml : " + failure.getMessage(), failure);
                }
            }
            if (!Files.exists(file)) throw new IOException("Fichier langue manquant : " + lang + ".yml");
            try {
                loaded.put(lang, YamlConfigurationLoader.builder().path(file).build().load());
                logger.info(MessageStyle.log("LANG", "<gray>Langue chargée : {}"), lang);
            } catch (IOException failure) {
                throw new IOException("Impossible de charger " + lang + ".yml : " + failure.getMessage(), failure);
            }
        }
        return Map.copyOf(loaded);
    }

    private void subscribeToLangChanges() {
        redisManager.subscribeToPlayerEvents(msg -> {
            if (msg.startsWith("LANG_CHANGED:")) {
                String rest = msg.substring("LANG_CHANGED:".length());
                int sep = rest.lastIndexOf(':');
                if (sep < 0) return;
                try {
                    UUID uuid = UUID.fromString(rest.substring(0, sep));
                    String lang = rest.substring(sep + 1);
                    if (SUPPORTED.contains(lang)) playerLangs.put(uuid, lang);
                } catch (IllegalArgumentException ignored) {}
            } else if (msg.startsWith("GRADE_CHANGED:") || msg.startsWith("GRADE_LOADED:")) {
                try {
                    UUID uuid = UUID.fromString(msg.substring(msg.indexOf(':') + 1));
                    loadPlayerGradeDisplay(uuid);
                } catch (IllegalArgumentException ignored) {}
            }
        });
    }

    public String get(UUID uuid, String key, Object... args) {
        return format(uuid, playerLangs.getOrDefault(uuid, defaultLang), key, args);
    }

    public String get(UUID uuid, String key, PlaceholderValues placeholders) {
        return MessageStyle.miniMessage(raw(playerLangs.getOrDefault(uuid, defaultLang), key),
                withGlobals(uuid, placeholders));
    }

    public String get(CommandSource source, String key, Object... args) {
        if (source instanceof Player p) return get(p.getUniqueId(), key, args);
        return format(null, defaultLang, key, args);
    }

    public String get(CommandSource source, String key, PlaceholderValues placeholders) {
        String language = source instanceof Player player
                ? playerLangs.getOrDefault(player.getUniqueId(), defaultLang) : defaultLang;
        UUID playerId = source instanceof Player player ? player.getUniqueId() : null;
        return MessageStyle.miniMessage(raw(language, key), withGlobals(playerId, placeholders));
    }

    private String format(UUID playerId, String lang, String key, Object... args) {
        String msg = raw(lang, key);
        PlaceholderValues globals = globalPlaceholders(playerId);
        if (args.length == 0 && !containsGlobalPlaceholder(msg, globals)) return msg;
        return MessageStyle.miniMessage(msg, PlaceholderValues.ordered(msg, globals, args));
    }

    private String raw(String lang, String key) {
        ConfigurationNode cfg = languages.getOrDefault(lang, languages.get(defaultLang));
        if (cfg == null) return "<tc><red>Langue indisponible : <white>" + key;
        String[] parts = key.split("\\.");
        String message = cfg.node((Object[]) parts).getString();
        if (message == null) {
            ConfigurationNode fallback = languages.get(defaultLang);
            if (fallback != null) message = fallback.node((Object[]) parts).getString();
        }
        return message == null ? "<tc><red>Clé de traduction manquante : <white>" + key : message;
    }

    public Component getComponent(UUID uuid, String key, Object... args) {
        return MessageStyle.component(get(uuid, key, args));
    }

    public Component getComponent(UUID uuid, String key, PlaceholderValues placeholders) {
        return MessageStyle.component(raw(playerLangs.getOrDefault(uuid, defaultLang), key),
                withGlobals(uuid, placeholders));
    }

    public Component getComponent(CommandSource source, String key, Object... args) {
        return MessageStyle.component(get(source, key, args));
    }

    public Component getComponent(CommandSource source, String key, PlaceholderValues placeholders) {
        String language = source instanceof Player player
                ? playerLangs.getOrDefault(player.getUniqueId(), defaultLang) : defaultLang;
        UUID playerId = source instanceof Player player ? player.getUniqueId() : null;
        return MessageStyle.component(raw(language, key), withGlobals(playerId, placeholders));
    }

    private PlaceholderValues globalPlaceholders(UUID playerId) {
        String instanceName = playerId == null ? "Velocity" : proxyServer.getPlayer(playerId)
                .flatMap(Player::getCurrentServer)
                .map(connection -> connection.getServerInfo().getName())
                .orElse("Velocity");
        PlaceholderValues.Builder placeholders = PlaceholderValues.builder().put("instance_name", instanceName);
        if (playerId != null) {
            String gradeDisplay = playerGradeDisplays.getOrDefault(playerId, DEFAULT_GRADE_DISPLAY);
            placeholders.putComponent("player_grade", MessageStyle.component(gradeDisplay));
        }
        return placeholders.build();
    }

    private PlaceholderValues withGlobals(UUID playerId, PlaceholderValues placeholders) {
        PlaceholderValues.Builder merged = PlaceholderValues.builder();
        placeholders.asMap().forEach(merged::putValue);
        globalPlaceholders(playerId).asMap().forEach(merged::putValue);
        return merged.build();
    }

    private boolean containsGlobalPlaceholder(String message, PlaceholderValues globals) {
        return globals.asMap().keySet().stream().anyMatch(name -> message.contains("{" + name + "}"));
    }

    public void loadPlayerLanguage(UUID uuid) {
        String lang = redisManager.getPlayerLanguage(uuid.toString());
        playerLangs.put(uuid, lang != null && SUPPORTED.contains(lang) ? lang : defaultLang);
        loadPlayerGradeDisplay(uuid);
    }

    private void loadPlayerGradeDisplay(UUID uuid) {
        String gradeDisplay = redisManager.getPlayerGradeDisplay(uuid.toString());
        if (gradeDisplay == null || gradeDisplay.isBlank()) playerGradeDisplays.remove(uuid);
        else playerGradeDisplays.put(uuid, gradeDisplay);
    }

    public void unloadPlayer(UUID uuid) {
        playerLangs.remove(uuid);
        playerGradeDisplays.remove(uuid);
    }

    public void reload() {
        try {
            languages = readLanguages();
        } catch (IOException failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        }
    }
}
