package fr.tropicube.core.ui;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.ConfigUpdater;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persists editor-managed UI files, completing restored languages from the installed plugin. */
public final class RuntimeUiBundle {
    private static final String ACTIVE_KEY = "runtime-ui:active";
    private static final String GENERATION_PREFIX = "runtime-ui:generation:";
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private final TropicubeCore plugin;

    public RuntimeUiBundle(TropicubeCore plugin) {
        this.plugin = plugin;
    }

    /** Restores the latest complete files from Redis before language and UI managers initialize. */
    public void restore() {
        String generation = plugin.getRedisManager().get(ACTIVE_KEY);
        if (generation == null) return;
        String manifest = plugin.getRedisManager().get(GENERATION_PREFIX + generation + ":manifest");
        if (manifest == null) {
            plugin.getLogger().warning("TROPICUBE > UI > Génération Redis incomplète ignorée : " + generation);
            return;
        }
        Map<String, String> hashes = parseManifest(manifest);
        files().forEach((id, path) -> {
            String expectedHash = hashes.get(id);
            if (expectedHash == null) return;
            String encoded = plugin.getRedisManager().get(GENERATION_PREFIX + generation + ":file:" + id);
            if (encoded == null) return;
            try {
                byte[] content = Base64.getDecoder().decode(encoded);
                if (content.length > MAX_FILE_BYTES || !hash(content).equals(expectedHash)) {
                    throw new IllegalArgumentException("contenu absent, trop volumineux ou hash invalide");
                }
                installFile(plugin, id, path, content);
            } catch (IOException | IllegalArgumentException failure) {
                plugin.getLogger().warning("TROPICUBE > UI > Restauration ignorée pour " + id + " : "
                        + failure.getMessage());
            }
        });
    }

    /** Completes older editor catalogs before publication, preserving their customized values. */
    static void installFile(Plugin plugin, String id, Path path, byte[] content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".runtime-ui-", ".tmp");
        try {
            Files.write(temporary, content);
            // Redis can predate the JAR: the startup merge performed before restore is insufficient.
            if (id.startsWith("core/languages/")) {
                ConfigUpdater.update(plugin, id.substring("core/".length()), temporary.toFile());
            }
            moveAtomically(temporary, path);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Publishes every file installed on this instance without expiring the last approved version. */
    public void publish() {
        String generation = UUID.randomUUID().toString();
        Map<String, byte[]> contents = new LinkedHashMap<>();
        files().forEach((id, path) -> {
            if (!Files.isRegularFile(path)) return;
            try {
                byte[] content = Files.readAllBytes(path);
                if (content.length > MAX_FILE_BYTES) throw new IllegalStateException(path + " dépasse 8 Mio");
                contents.put(id, content);
            } catch (IOException failure) {
                throw new IllegalStateException("Lecture impossible de " + path, failure);
            }
        });
        if (contents.isEmpty()) throw new IllegalStateException("Aucun fichier d'interface à publier");
        StringBuilder manifest = new StringBuilder();
        contents.forEach((id, content) -> {
            plugin.getRedisManager().setPersistent(GENERATION_PREFIX + generation + ":file:" + id,
                    Base64.getEncoder().encodeToString(content));
            manifest.append(id).append('=').append(hash(content)).append('\n');
        });
        plugin.getRedisManager().setPersistent(GENERATION_PREFIX + generation + ":manifest", manifest.toString());
        plugin.getRedisManager().setPersistent(ACTIVE_KEY, generation);
    }

    private Map<String, Path> files() {
        Path plugins = plugin.getDataFolder().toPath().getParent();
        Map<String, Path> result = new LinkedHashMap<>();
        for (String language : java.util.List.of("fr", "en", "de", "es")) {
            result.put("core/languages/" + language + ".yml",
                    plugins.resolve("TropicubeCore/languages/" + language + ".yml"));
        }
        result.put("core/menus.yml", plugins.resolve("TropicubeCore/menus.yml"));
        result.put("lobby/menus.yml", plugins.resolve("TropicubeLobby/menus.yml"));
        result.put("lobby/scoreboards.yml", plugins.resolve("TropicubeLobby/scoreboards.yml"));
        result.put("lobby/tablists.yml", plugins.resolve("TropicubeLobby/tablists.yml"));
        result.put("sheepwars/menus.yml", plugins.resolve("TropicubeSheepwars/menus.yml"));
        result.put("sheepwars/scoreboards.yml", plugins.resolve("TropicubeSheepwars/scoreboards.yml"));
        result.put("sheepwars/tablists.yml", plugins.resolve("TropicubeSheepwars/tablists.yml"));
        return result;
    }

    private static Map<String, String> parseManifest(String manifest) {
        Map<String, String> result = new LinkedHashMap<>();
        manifest.lines().forEach(line -> {
            int separator = line.indexOf('=');
            if (separator > 0) result.put(line.substring(0, separator), line.substring(separator + 1));
        });
        return result;
    }

    private static String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
