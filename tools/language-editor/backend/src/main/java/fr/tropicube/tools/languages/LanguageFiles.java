package fr.tropicube.tools.languages;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Discovers, reads and validates editable language resources below a repository root. */
final class LanguageFiles {
    static final List<String> LANGUAGES = List.of("fr", "en", "de", "es");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");
    private static final Pattern TAG = Pattern.compile("(?<!\\\\)<([^<>]+)>");
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "aqua", "b", "blue", "bold", "click", "dark_aqua", "dark_blue", "dark_gray",
            "dark_green", "dark_purple", "dark_red", "gold", "gray", "green", "italic",
            "light_purple", "obfuscated", "red", "reset", "strikethrough", "sw", "tc",
            "u", "underlined", "white", "yellow");

    private final Path repository;
    private final Yaml yaml = safeYaml();

    LanguageFiles(Path repository) {
        this.repository = repository.toAbsolutePath().normalize();
    }

    List<LanguageSet> discover() throws IOException {
        List<LanguageSet> result = new ArrayList<>();
        try (var paths = Files.find(repository, 6, (path, attributes) -> attributes.isRegularFile()
                && path.endsWith(Path.of("src", "main", "resources", "languages", "fr.yml")))) {
            for (Path french : paths.sorted().toList()) {
                Path directory = french.getParent();
                if (!LANGUAGES.stream().allMatch(language -> Files.isRegularFile(directory.resolve(language + ".yml")))) {
                    continue;
                }
                Path module = directory.getParent().getParent().getParent().getParent();
                String id = module.getFileName().toString();
                Path mirror = dockerMirror(id);
                result.add(new LanguageSet(id, relative(directory), mirror == null ? null : relative(mirror)));
            }
        }
        return result;
    }

    Map<String, FileSnapshot> read(LanguageSet set) throws IOException {
        Path directory = resolve(set.sourceDirectory());
        Map<String, FileSnapshot> files = new LinkedHashMap<>();
        for (String language : LANGUAGES) {
            String content = Files.readString(directory.resolve(language + ".yml"), StandardCharsets.UTF_8);
            files.put(language, new FileSnapshot(content, hash(content)));
        }
        return files;
    }

    List<Diagnostic> validate(Map<String, String> documents) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, Map<String, Object>> leaves = new LinkedHashMap<>();
        for (String language : LANGUAGES) {
            String content = documents.get(language);
            if (content == null) {
                diagnostics.add(new Diagnostic(language, "", "missing-document", "Document manquant"));
                continue;
            }
            try {
                Object loaded = yaml.load(content);
                if (!(loaded instanceof Map<?, ?> map)) {
                    diagnostics.add(new Diagnostic(language, "", "invalid-root", "La racine YAML doit être un objet"));
                    continue;
                }
                Map<String, Object> flat = new LinkedHashMap<>();
                flatten("", map, flat);
                leaves.put(language, flat);
                validateValues(language, flat, diagnostics);
            } catch (RuntimeException exception) {
                diagnostics.add(new Diagnostic(language, "", "invalid-yaml", exception.getMessage()));
            }
        }
        Map<String, Object> french = leaves.get("fr");
        if (french != null) {
            for (String language : LANGUAGES.subList(1, LANGUAGES.size())) {
                Map<String, Object> translated = leaves.get(language);
                if (translated == null) continue;
                if (!french.keySet().equals(translated.keySet())) {
                    Set<String> missing = new LinkedHashSet<>(french.keySet());
                    missing.removeAll(translated.keySet());
                    Set<String> extra = new LinkedHashSet<>(translated.keySet());
                    extra.removeAll(french.keySet());
                    diagnostics.add(new Diagnostic(language, "", "key-parity",
                            "Clés manquantes=" + missing + ", supplémentaires=" + extra));
                }
                for (String key : french.keySet()) {
                    if (!translated.containsKey(key)) continue;
                    if (!placeholders(french.get(key)).equals(placeholders(translated.get(key)))) {
                        diagnostics.add(new Diagnostic(language, key, "placeholder-parity",
                                "Les placeholders diffèrent du français"));
                    }
                    if (!formattingTags(french.get(key)).equals(formattingTags(translated.get(key)))) {
                        diagnostics.add(new Diagnostic(language, key, "palette-parity",
                                "La palette MiniMessage diffère du français"));
                    }
                }
            }
        }
        return diagnostics;
    }

    void apply(LanguageSet set, Map<String, String> expectedHashes, Map<String, String> documents) throws IOException {
        List<Diagnostic> diagnostics = validate(documents);
        if (!diagnostics.isEmpty()) throw new IllegalArgumentException("Validation refusée: " + diagnostics);
        Path source = resolve(set.sourceDirectory());
        for (String language : LANGUAGES) {
            String current = Files.readString(source.resolve(language + ".yml"), StandardCharsets.UTF_8);
            if (!hash(current).equals(expectedHashes.get(language))) {
                throw new IllegalStateException(language + ".yml a été modifié depuis son ouverture");
            }
        }
        List<Path> targets = new ArrayList<>();
        for (String language : LANGUAGES) targets.add(source.resolve(language + ".yml"));
        if (set.mirrorDirectory() != null) {
            Path mirror = resolve(set.mirrorDirectory());
            Files.createDirectories(mirror);
            for (String language : LANGUAGES) targets.add(mirror.resolve(language + ".yml"));
        }
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        List<Path> temporaries = new ArrayList<>();
        try {
            for (Path target : targets) {
                originals.put(target, Files.exists(target) ? Files.readAllBytes(target) : null);
                String language = target.getFileName().toString().substring(0, 2);
                Path temporary = Files.createTempFile(target.getParent(), ".language-editor-", ".tmp");
                Files.writeString(temporary, normalize(documents.get(language)), StandardCharsets.UTF_8);
                temporaries.add(temporary);
            }
            for (int index = 0; index < targets.size(); index++) {
                Files.move(temporaries.get(index), targets.get(index), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            for (Map.Entry<Path, byte[]> entry : originals.entrySet()) {
                if (entry.getValue() != null) Files.write(entry.getKey(), entry.getValue());
                else Files.deleteIfExists(entry.getKey());
            }
            throw failure;
        } finally {
            for (Path temporary : temporaries) Files.deleteIfExists(temporary);
        }
    }

    private void validateValues(String language, Map<String, Object> leaves, List<Diagnostic> diagnostics) {
        leaves.forEach((key, value) -> {
            List<?> values = value instanceof List<?> list ? list : List.of(value);
            for (Object item : values) {
                if (!(item instanceof String text)) {
                    diagnostics.add(new Diagnostic(language, key, "invalid-value", "Seuls les textes et listes de textes sont acceptés"));
                    continue;
                }
                Matcher matcher = TAG.matcher(text);
                while (matcher.find()) {
                    String name = matcher.group(1).replaceFirst("^/", "");
                    int separator = name.indexOf(':');
                    if (separator >= 0) name = name.substring(0, separator);
                    if (!name.matches("#[0-9a-fA-F]{6}") && !ALLOWED_TAGS.contains(name.toLowerCase())) {
                        diagnostics.add(new Diagnostic(language, key, "unknown-tag", "Balise inconnue <" + name + ">"));
                    }
                }
            }
        });
    }

    private static void flatten(String prefix, Map<?, ?> source, Map<String, Object> target) {
        source.forEach((key, value) -> {
            String path = prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key;
            if (value instanceof Map<?, ?> nested) flatten(path, nested, target);
            else target.put(path, value);
        });
    }

    private static List<String> placeholders(Object value) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(String.valueOf(value));
        while (matcher.find()) found.add(matcher.group());
        return found;
    }

    private static Set<String> formattingTags(Object value) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = TAG.matcher(String.valueOf(value));
        while (matcher.find()) found.add(matcher.group(1).toLowerCase(Locale.ROOT));
        return found;
    }

    private Path dockerMirror(String module) {
        String plugin = switch (module) {
            case "tropicube-core" -> "TropicubeCore";
            case "tropicube-velocity" -> "TropicubeVelocity";
            default -> null;
        };
        if (plugin == null) return null;
        Path candidate = repository.resolve("dockerfiles/configs").resolve(plugin).resolve("languages");
        return Files.isDirectory(candidate) ? candidate : null;
    }

    private Path resolve(String relative) {
        Path path = repository.resolve(relative).normalize();
        if (!path.startsWith(repository)) throw new IllegalArgumentException("Chemin hors dépôt");
        return path;
    }

    private String relative(Path path) {
        return repository.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String normalize(String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.endsWith("\n") ? normalized : normalized + "\n";
    }

    static Yaml safeYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setCodePointLimit(8 * 1024 * 1024);
        return new Yaml(new SafeConstructor(options));
    }

    record LanguageSet(String id, String sourceDirectory, String mirrorDirectory) {}
    record FileSnapshot(String content, String hash) {}
    record Diagnostic(String language, String key, String code, String message) {}
}
