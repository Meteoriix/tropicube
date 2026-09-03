package fr.tropicube.tools.languages;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Discovers and validates versioned scoreboard, tablist, and menu manifests owned by each Paper module. */
final class UiFiles {
    private static final Set<String> NAMES = Set.of("scoreboards.yml", "tablists.yml", "menus.yml");
    private static final java.util.regex.Pattern IDENTIFIER = java.util.regex.Pattern.compile("[a-z][a-z0-9_-]*");
    private static final java.util.regex.Pattern MATERIAL = java.util.regex.Pattern.compile("[A-Z][A-Z0-9_]*");

    private final Path repository;
    private final Yaml yaml = LanguageFiles.safeYaml();

    UiFiles(Path repository) {
        this.repository = repository.toAbsolutePath().normalize();
    }

    List<UiSnapshot> readAll() throws IOException {
        List<UiSnapshot> result = new ArrayList<>();
        try (var paths = Files.find(repository, 6, (path, attributes) -> attributes.isRegularFile()
                && NAMES.contains(path.getFileName().toString())
                && path.endsWith(Path.of("src", "main", "resources", path.getFileName().toString())))) {
            for (Path path : paths.sorted().toList()) {
                Path module = path.getParent().getParent().getParent().getParent();
                String content = Files.readString(path, StandardCharsets.UTF_8);
                Path mirror = mirror(module.getFileName().toString(), path.getFileName().toString());
                result.add(new UiSnapshot(module.getFileName().toString() + ":" + path.getFileName(),
                        module.getFileName().toString(), path.getFileName().toString().replace(".yml", ""),
                        relative(path), mirror == null ? null : relative(mirror), content, hash(content)));
            }
        }
        return result;
    }

    List<String> validate(String type, String content) {
        List<String> errors = new ArrayList<>();
        Object loaded;
        try {
            loaded = yaml.load(content);
        } catch (RuntimeException failure) {
            return List.of("YAML invalide : " + failure.getMessage());
        }
        if (!(loaded instanceof Map<?, ?> root)) return List.of("La racine doit être un objet YAML");
        if (!(root.get("version") instanceof Number version) || version.intValue() != 1) {
            errors.add("version doit valoir 1");
        }
        Object definitions = root.get(type);
        if (!(definitions instanceof Map<?, ?> entries) || entries.isEmpty()) {
            errors.add("La section " + type + " doit contenir au moins une définition");
        } else if ("scoreboards".equals(type)) {
            validateScoreboards(entries, errors);
        } else if ("tablists".equals(type)) {
            validateTablists(entries, errors);
        } else {
            validateMenus(entries, errors);
        }
        return List.copyOf(errors);
    }

    private static void validateTablists(Map<?, ?> tablists, List<String> errors) {
        tablists.forEach((id, raw) -> {
            if (!(raw instanceof Map<?, ?> tablist)) {
                errors.add(id + " doit être un objet");
                return;
            }
            if (!(tablist.get("variants") instanceof Map<?, ?> variants) || variants.isEmpty()) {
                errors.add(id + ".variants est requis");
                return;
            }
            variants.forEach((variantId, rawVariant) -> {
                if (!(rawVariant instanceof Map<?, ?> variant)) {
                    errors.add(id + "." + variantId + " doit être un objet");
                    return;
                }
                for (String key : List.of("header-key", "footer-key")) {
                    if (!(variant.get(key) instanceof String text) || text.isBlank()) {
                        errors.add(id + "." + variantId + "." + key + " est requis");
                    }
                }
            });
        });
    }

    Map<String, UiSnapshot> apply(Map<String, String> expectedHashes, Map<String, String> documents) throws IOException {
        Map<String, UiSnapshot> snapshots = readAll().stream()
                .collect(LinkedHashMap::new, (map, value) -> map.put(value.id(), value), Map::putAll);
        for (Map.Entry<String, String> entry : documents.entrySet()) {
            UiSnapshot snapshot = snapshots.get(entry.getKey());
            if (snapshot == null) throw new IllegalArgumentException("Manifeste inconnu : " + entry.getKey());
            List<String> errors = validate(snapshot.type(), entry.getValue());
            if (!errors.isEmpty()) throw new IllegalArgumentException(snapshot.id() + " : " + errors);
            if (!snapshot.hash().equals(expectedHashes.get(entry.getKey()))) {
                throw new IllegalStateException(snapshot.sourcePath() + " a été modifié depuis son ouverture");
            }
        }
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> entry : documents.entrySet()) {
                UiSnapshot snapshot = snapshots.get(entry.getKey());
                for (String targetName : new String[] {snapshot.sourcePath(), snapshot.mirrorPath()}) {
                    if (targetName == null) continue;
                    Path target = resolve(targetName);
                    Files.createDirectories(target.getParent());
                    originals.put(target, Files.exists(target) ? Files.readAllBytes(target) : null);
                    Path temporary = Files.createTempFile(target.getParent(), ".ui-editor-", ".tmp");
                    try {
                        String normalized = normalize(entry.getValue());
                        Files.writeString(temporary, normalized, StandardCharsets.UTF_8);
                        Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } finally {
                        Files.deleteIfExists(temporary);
                    }
                }
            }
        } catch (IOException failure) {
            for (Map.Entry<Path, byte[]> original : originals.entrySet()) {
                if (original.getValue() == null) Files.deleteIfExists(original.getKey());
                else Files.write(original.getKey(), original.getValue());
            }
            throw failure;
        }
        return readAll().stream().collect(LinkedHashMap::new,
                (map, value) -> map.put(value.id(), value), Map::putAll);
    }

    private static void validateScoreboards(Map<?, ?> scoreboards, List<String> errors) {
        scoreboards.forEach((id, raw) -> {
            if (!(raw instanceof Map<?, ?> scoreboard)) {
                errors.add(id + " doit être un objet");
                return;
            }
            if (!(scoreboard.get("title-key") instanceof String)) errors.add(id + ".title-key est requis");
            if (!(scoreboard.get("variants") instanceof Map<?, ?> variants) || variants.isEmpty()) {
                errors.add(id + ".variants est requis");
                return;
            }
            variants.forEach((variant, value) -> {
                if (!(value instanceof Map<?, ?> definition)
                        || !(definition.get("lines") instanceof List<?> lines)
                        || lines.isEmpty() || lines.size() > 15) {
                    errors.add(id + "." + variant + " doit contenir entre 1 et 15 lignes");
                    return;
                }
                for (int index = 0; index < lines.size(); index++) {
                    Object rawLine = lines.get(index);
                    if (!(rawLine instanceof Map<?, ?> line)) {
                        errors.add(id + "." + variant + ".lines[" + index + "] doit être un objet");
                        continue;
                    }
                    boolean blank = Boolean.TRUE.equals(line.get("blank"));
                    boolean key = line.get("key") instanceof String text && !text.isBlank();
                    if (blank == key) errors.add(id + "." + variant + ".lines[" + index
                            + "] doit définir exactement key ou blank: true");
                }
            });
        });
    }

    private static void validateMenus(Map<?, ?> menus, List<String> errors) {
        menus.forEach((id, raw) -> {
            if (!(raw instanceof Map<?, ?> menu)) {
                errors.add(id + " doit être un objet");
                return;
            }
            int rows = menu.get("rows") instanceof Number number ? number.intValue() : -1;
            if (rows < 1 || rows > 6) errors.add(id + ".rows doit être compris entre 1 et 6");
            if (!(menu.get("title-key") instanceof String)) errors.add(id + ".title-key est requis");
            if (menu.get("buttons") instanceof Map<?, ?> buttons) {
                Set<Integer> occupied = new java.util.HashSet<>();
                buttons.forEach((buttonId, buttonRaw) -> {
                    if (!(buttonRaw instanceof Map<?, ?> button)) {
                        errors.add(id + ".buttons." + buttonId + " doit être un objet");
                        return;
                    }
                    int slot = button.get("slot") instanceof Number number ? number.intValue() : -1;
                    if (slot < 0 || slot >= rows * 9) errors.add(id + "." + buttonId + ".slot est hors inventaire");
                    else if (!occupied.add(slot)) errors.add(id + " contient plusieurs boutons au slot " + slot);
                    if (!(button.get("material") instanceof String)) errors.add(id + "." + buttonId + ".material est requis");
                    else if (!MATERIAL.matcher(String.valueOf(button.get("material"))).matches())
                        errors.add(id + "." + buttonId + ".material doit être un identifiant Minecraft en majuscules");
                    if (!(button.get("action") instanceof String action) || !IDENTIFIER.matcher(action).matches())
                        errors.add(id + "." + buttonId + ".action est invalide");
                    for (String key : List.of("name-key", "lore-key")) {
                        Object value = button.get(key);
                        if (value != null && (!(value instanceof String text) || text.isBlank()))
                            errors.add(id + "." + buttonId + "." + key + " est invalide");
                    }
                });
            }
            if (menu.get("dynamic-regions") instanceof Map<?, ?> regions) regions.forEach((regionId, rawRegion) -> {
                if (!(rawRegion instanceof Map<?, ?> region) || !(region.get("slots") instanceof List<?> slots)) {
                    errors.add(id + ".dynamic-regions." + regionId + ".slots est requis");
                    return;
                }
                for (Object rawSlot : slots) {
                    int slot = rawSlot instanceof Number number ? number.intValue() : -1;
                    if (slot < 0 || slot >= rows * 9) errors.add(id + ".dynamic-regions." + regionId
                            + " contient un slot hors inventaire");
                }
                Object action = region.get("template-action");
                if (!(action instanceof String text) || !IDENTIFIER.matcher(text).matches())
                    errors.add(id + ".dynamic-regions." + regionId + ".template-action est invalide");
            });
        });
    }

    private Path mirror(String module, String fileName) {
        String plugin = switch (module) {
            case "tropicube-core" -> "TropicubeCore";
            case "tropicube-lobby" -> "TropicubeLobby";
            case "tropicube-sheepwars" -> "TropicubeSheepwars";
            default -> null;
        };
        if (plugin == null) return null;
        return repository.resolve("dockerfiles/configs").resolve(plugin).resolve(fileName);
    }

    private Path resolve(String path) {
        Path resolved = repository.resolve(path).normalize();
        if (!resolved.startsWith(repository)) throw new IllegalArgumentException("Chemin de manifeste hors dépôt");
        return resolved;
    }

    private String relative(Path path) {
        return repository.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String normalize(String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.endsWith("\n") ? normalized : normalized + "\n";
    }

    record UiSnapshot(String id, String module, String type, String sourcePath, String mirrorPath,
                      String content, String hash) { }
}
