package fr.tropicube.velocity.managers;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import fr.tropicube.docker.client.RedisManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Charge les traductions du proxy et résout la langue de chaque joueur via Redis. */
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
                    logger.warn("[Lang] Could not copy {}.yml: {}", lang, e.getMessage());
                }
            }
            if (Files.exists(file)) {
                try {
                    loaded.put(lang, YamlConfigurationLoader.builder().path(file).build().load());
                    logger.info("[Lang] Loaded: {}", lang);
                } catch (IOException e) {
                    logger.warn("[Lang] Failed to load {}.yml: {}", lang, e.getMessage());
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

    public String get(CommandSource source, String key, Object... args) {
        if (source instanceof Player p) return get(p.getUniqueId(), key, args);
        return format(defaultLang, key, args);
    }

    private String format(String lang, String key, Object... args) {
        ConfigurationNode cfg = languages.getOrDefault(lang, languages.get(defaultLang));
        if (cfg == null) return "<red>[lang:" + key + "]";
        String[] parts = key.split("\\.");
        String msg = cfg.node((Object[]) parts).getString();
        if (msg == null) {
            ConfigurationNode fb = languages.get(defaultLang);
            if (fb != null) msg = fb.node((Object[]) parts).getString();
        }
        if (msg == null) return "<red>[lang:" + key + "]";
        for (int i = 0; i < args.length; i++) msg = msg.replace("{" + i + "}", String.valueOf(args[i]));
        return msg;
    }

    public Component getComponent(UUID uuid, String key, Object... args) {
        return MiniMessage.miniMessage().deserialize(get(uuid, key, args));
    }

    public Component getComponent(CommandSource source, String key, Object... args) {
        return MiniMessage.miniMessage().deserialize(get(source, key, args));
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
