package fr.tropicube.velocity.managers;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
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

/** Loads proxy translations and resolves each player's language via Redis. */
public class VelocityLanguageManager {

    public static final List<String> SUPPORTED = List.of("fr", "en", "es", "de");

    private volatile Map<String, ConfigurationNode> languages = Map.of();
    private final Map<UUID, String> playerLangs = new ConcurrentHashMap<>();
    private final RedisManager redisManager;
    private final Path dataDir;
    private final Logger logger;
    private final String defaultLang;

    public VelocityLanguageManager(Path dataDir, RedisManager redisManager, Logger logger, String defaultLang) {
        this.dataDir = dataDir;
        this.redisManager = redisManager;
        this.logger = logger;
        this.defaultLang = SUPPORTED.contains(defaultLang) ? defaultLang : "fr";
        loadLanguages();
        subscribeToLangChanges();
    }

    private void loadLanguages() {
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
                } catch (IOException e) {
                    logger.warn(MessageStyle.log("LANG", "<yellow>Impossible de copier {}.yml : {}"), lang, e.getMessage());
                }
            }
            if (Files.exists(file)) {
                try {
                    loaded.put(lang, YamlConfigurationLoader.builder().path(file).build().load());
                    logger.info(MessageStyle.log("LANG", "<gray>Langue chargée : {}"), lang);
                } catch (IOException e) {
                    logger.warn(MessageStyle.log("LANG", "<yellow>Impossible de charger {}.yml : {}"), lang, e.getMessage());
                }
            }
        }
        if (!loaded.isEmpty()) languages = Map.copyOf(loaded);
    }

    private void subscribeToLangChanges() {
        redisManager.subscribeToPlayerEvents(msg -> {
            if (!msg.startsWith("LANG_CHANGED:")) return;
            String rest = msg.substring("LANG_CHANGED:".length());
            int sep = rest.lastIndexOf(':');
            if (sep < 0) return;
            String uuidStr = rest.substring(0, sep);
            String lang = rest.substring(sep + 1);
            try {
                UUID uuid = UUID.fromString(uuidStr);
                if (SUPPORTED.contains(lang)) playerLangs.put(uuid, lang);
            } catch (IllegalArgumentException ignored) {}
        });
    }

    public String get(UUID uuid, String key, Object... args) {
        return format(playerLangs.getOrDefault(uuid, defaultLang), key, args);
    }

    public String get(UUID uuid, String key, PlaceholderValues placeholders) {
        return MessageStyle.miniMessage(raw(playerLangs.getOrDefault(uuid, defaultLang), key), placeholders);
    }

    public String get(CommandSource source, String key, Object... args) {
        if (source instanceof Player p) return get(p.getUniqueId(), key, args);
        return format(defaultLang, key, args);
    }

    public String get(CommandSource source, String key, PlaceholderValues placeholders) {
        String language = source instanceof Player player
                ? playerLangs.getOrDefault(player.getUniqueId(), defaultLang) : defaultLang;
        return MessageStyle.miniMessage(raw(language, key), placeholders);
    }

    private String format(String lang, String key, Object... args) {
        String msg = raw(lang, key);
        return args.length == 0 ? msg : MessageStyle.miniMessage(msg, PlaceholderValues.ordered(msg, args));
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
        return MessageStyle.component(raw(playerLangs.getOrDefault(uuid, defaultLang), key), placeholders);
    }

    public Component getComponent(CommandSource source, String key, Object... args) {
        return MessageStyle.component(get(source, key, args));
    }

    public Component getComponent(CommandSource source, String key, PlaceholderValues placeholders) {
        String language = source instanceof Player player
                ? playerLangs.getOrDefault(player.getUniqueId(), defaultLang) : defaultLang;
        return MessageStyle.component(raw(language, key), placeholders);
    }

    public void loadPlayerLanguage(UUID uuid) {
        String lang = redisManager.getPlayerLanguage(uuid.toString());
        playerLangs.put(uuid, lang != null && SUPPORTED.contains(lang) ? lang : defaultLang);
    }

    public void unloadPlayer(UUID uuid) {
        playerLangs.remove(uuid);
    }

    public void reload() {
        loadLanguages();
    }
}
