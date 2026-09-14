package fr.tropicube.core.util;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import fr.tropicube.core.guild.GuildPresentation;
import fr.tropicube.core.guild.GuildService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Audits actual Java literals and enumerated dynamic families across every reactor module. */
class PluginLanguageReferencesTest {
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9-]*\\.[a-z0-9_.-]+");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][a-z0-9_]*)}");
    private static final Set<String> TRANSLATION_METHODS = Set.of("component", "getComponent", "getComponentForLang", "getList",
            "getMessage", "message", "messageText", "translate");
    private record Reference(String module, String key, String origin) { }

    @Test
    void allPluginReferencesExistWithoutLanguageFallback() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<Path> modules;
        try (var paths = Files.list(root)) {
            modules = paths.filter(path -> path.getFileName().toString().startsWith("tropicube-")
                    && Files.exists(path.resolve("pom.xml"))).sorted().toList();
        }
        Map<String, YamlConfiguration> catalogs = new HashMap<>();
        for (String owner : List.of("core", "velocity")) for (String lang : List.of("fr", "en", "de", "es")) {
            catalogs.put(owner + "/" + lang, YamlConfiguration.loadConfiguration(root.resolve(
                    "tropicube-" + owner + "/src/main/resources/languages/" + lang + ".yml").toFile()));
        }
        Set<String> namespaces = new HashSet<>();
        catalogs.values().forEach(catalog -> namespaces.addAll(catalog.getKeys(false)));
        List<Reference> references = new ArrayList<>();
        Map<String, Set<String>> enums = new HashMap<>();
        Set<String> dynamic = new TreeSet<>();
        List<String> errors = new ArrayList<>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A full JDK is required for the source audit");
        try (var manager = compiler.getStandardFileManager(null, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            for (Path module : modules) {
                String moduleName = module.getFileName().toString();
                String owner = moduleName.equals("tropicube-velocity") ? "velocity" : "core";
                Path sources = module.resolve("src/main/java");
                if (!Files.isDirectory(sources)) continue;
                List<Path> files;
                try (var paths = Files.walk(sources)) { files = paths.filter(p -> p.toString().endsWith(".java")).toList(); }
                if (files.isEmpty()) continue;
                Set<String> configurationKeys = new HashSet<>();
                Path config = module.resolve("src/main/resources/config.yml");
                if (Files.exists(config)) configurationKeys.addAll(YamlConfiguration.loadConfiguration(config.toFile()).getKeys(true));
                var task = (JavacTask) compiler.getTask(null, manager, null, List.of("-proc:none"), null,
                        manager.getJavaFileObjectsFromPaths(files));
                for (CompilationUnitTree unit : task.parse()) {
                    new TreePathScanner<Void, Void>() {
                        @Override public Void visitClass(ClassTree node, Void ignored) {
                            if (node.getKind() == Tree.Kind.ENUM) {
                                Set<String> values = new TreeSet<>();
                                for (Tree member : node.getMembers()) if (member instanceof VariableTree variable
                                        && variable.getInitializer() instanceof NewClassTree)
                                    values.add(variable.getName().toString().toLowerCase(Locale.ROOT));
                                enums.put(moduleName + "/" + node.getSimpleName(), values);
                            }
                            return super.visitClass(node, ignored);
                        }

                        @Override public Void visitLiteral(LiteralTree node, Void ignored) {
                            if (!(node.getValue() instanceof String key) || !KEY.matcher(key).matches()) return null;
                            if (key.endsWith(".yml") || key.startsWith("cosmetics.entries.")) return null;
                            Tree parent = getCurrentPath().getParentPath().getLeaf();
                            boolean translationCall = parent instanceof MethodInvocationTree call
                                    && (TRANSLATION_METHODS.contains(method(call)) || isTranslationGet(call));
                            if (!translationCall && (!namespaces.contains(key.substring(0, key.indexOf('.')))
                                    || configurationKeys.contains(key))) return null;
                            if (key.endsWith("-") || key.endsWith(".")) dynamic.add(key);
                            else references.add(new Reference(owner, key, unit.getSourceFile().getName()));
                            return null;
                        }

                        @Override public Void visitMethodInvocation(MethodInvocationTree call, Void ignored) {
                            // Fixed-arity varargs adapters insert all values following the key.
                            boolean guildItem = method(call).equals("item") && unit.getSourceFile().getName().endsWith("GuildGUI.java");
                            if (Set.of("component", "getComponent", "getComponentForLang", "message", "messageText").contains(method(call))
                                    || isTranslationGet(call) || guildItem) {
                                var args = call.getArguments();
                                for (int i = 0; i < args.size(); i++) if (args.get(i) instanceof LiteralTree literal
                                        && literal.getValue() instanceof String key && KEY.matcher(key).matches()) {
                                    String template = catalogs.get(owner + "/fr").getString(key);
                                    if (template == null) continue;
                                    Set<String> values = new LinkedHashSet<>();
                                    var matcher = PLACEHOLDER.matcher(template);
                                    while (matcher.find()) values.add(matcher.group(1));
                                    int provided = args.size() - i - 1 - (guildItem ? 1 : 0);
                                    // Named/array adapters are verified by their dedicated renderer tests.
                                    boolean namedOrArray = provided == 1 && !(args.get(i + 1) instanceof LiteralTree)
                                            && (args.get(i + 1).toString().contains("Placeholders")
                                            || args.get(i + 1).toString().contains("PlaceholderValues")
                                            || args.get(i + 1).toString().endsWith(".build()")
                                            || args.get(i + 1).toString().matches("arguments|args|values|placeholders"));
                                    if (!namedOrArray && provided != values.size()) errors.add(
                                            unit.getSourceFile().getName() + ": " + key + " expects " + values.size() + " values, got " + provided);
                                }
                            }
                            return super.visitMethodInvocation(call, ignored);
                        }
                    }.scan(unit, null);
                }
                Path resources = module.resolve("src/main/resources");
                if (Files.isDirectory(resources)) try (var paths = Files.walk(resources)) {
                    for (Path file : paths.filter(p -> p.toString().endsWith(".yml") && !p.toString().contains("languages")).toList()) {
                        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
                        resourceReferences(yaml, "", owner, file.toString(), namespaces, references);
                    }
                }
            }
        }
        Map<String, Set<String>> families = new TreeMap<>();
        families.put("cosmetics.result-", java.util.Arrays.stream(fr.tropicube.core.cosmetic.CosmeticService.Result.values())
                .map(value -> value.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-')).collect(java.util.stream.Collectors.toSet()));
        families.put("cosmetics.access-", Set.of("free", "level", "currency", "vip"));
        families.put("cosmetics.filter-", Set.of("all", "available", "locked"));
        for (String prefix : List.of("cosmetics.guide-", "cosmetics.help-", "cosmetics.open-")) families.put(prefix, Set.of("play", "progress", "social", "customize"));
        try (var input = Files.newInputStream(root.resolve("tropicube-core/src/main/resources/cosmetics.yml"))) {
            families.put("cosmetics.name-", fr.tropicube.core.cosmetic.CosmeticCatalog.load(input).entries().stream()
                    .map(fr.tropicube.core.cosmetic.CosmeticCatalog.Entry::id).collect(java.util.stream.Collectors.toSet()));
        }
        Set<String> ranks = enums.get("tropicube-sheepwars/RankTier");
        for (String prefix : List.of("sw.rank-", "center.rank-", "season.reward-title-", "season.reward-badge-")) families.put(prefix, ranks);
        families.put("sw.sb-class-", enums.get("tropicube-sheepwars/PlayerClass"));
        for (String[] definition : List.of(new String[]{"class", "PlayerClass"}, new String[]{"kit", "PlayerKit"}, new String[]{"sheep", "SheepType"})) {
            Set<String> suffixes = new TreeSet<>();
            for (String value : enums.get("tropicube-sheepwars/" + definition[1])) {
                suffixes.add(value + "-name"); suffixes.add(value + "-description");
            }
            families.put("sw.catalog-" + definition[0] + "-", suffixes);
        }
        families.put("sw.mastery-branch-", Set.of("a-name", "b-name"));
        families.put("economy.admin-", Set.of("set", "add", "remove"));
        families.put("center.notification-category-value-", Set.of("all", "guild", "social", "system", "mission", "moderation"));
        families.put("center.mission-rotation-", Set.of("daily", "weekly"));
        Set<String> missionEvents = new HashSet<>();
        var missions = YamlConfiguration.loadConfiguration(root.resolve("tropicube-core/src/main/resources/missions.yml").toFile());
        missions.getValues(true).forEach((key, value) -> { if (key.endsWith(".event")) missionEvents.add(value.toString().toLowerCase(Locale.ROOT).replace('_', '-')); });
        families.put("center.mission-event-", missionEvents);
        families.put("lobby.server-filter-", enums.get("tropicube-lobby/Filter").stream()
                .map(value -> value.replace('_', '-')).collect(java.util.stream.Collectors.toSet()));
        Set<String> settings = new HashSet<>();
        for (String type : List.of("ProfileVisibility", "MessagePrivacy", "LobbyVisibility")) enums.get("tropicube-core/" + type).forEach(value -> settings.add(value.replace('_', '-')));
        families.put("lobby.settings-value-", settings);
        families.put("fk.phase-", Set.of("preparation", "pvp", "assault", "sudden_death"));
        families.put("fk.team-", Set.of("blue", "red", "green", "yellow", "orange", "spectator"));
        families.put("fk.kit-", Set.of("miner", "farmer", "scout", "enchanter"));
        var lobby = YamlConfiguration.loadConfiguration(root.resolve("tropicube-lobby/src/main/resources/config.yml").toFile());
        // Grade entries are configuration-driven; the configured grade identifiers are audited below.
        Set<String> grades = new HashSet<>();
        for (Map<?, ?> entry : lobby.getMapList("vip-shop.entries"))
            grades.add(entry.get("grade-key").toString().toLowerCase(Locale.ROOT).replace('_', '-'));
        for (String prefix : List.of("lobby.shop-active-", "lobby.shop-soon-", "lobby.vip-perks-")) families.put(prefix, grades);
        for (String prefix : dynamic) {
            Set<String> suffixes = families.get(prefix);
            if (suffixes == null || suffixes.isEmpty()) { errors.add("Uncovered dynamic translation family: " + prefix); continue; }
            for (String suffix : suffixes) references.add(new Reference("core", prefix + suffix, "dynamic family " + prefix));
        }
        for (var result : GuildService.Result.values()) references.add(new Reference("core", GuildPresentation.resultKey(result), "GuildService.Result"));
        for (Reference reference : references) for (String language : List.of("fr", "en", "de", "es")) {
            Object value = catalogs.get(reference.module() + "/" + language).get(reference.key());
            if (!(value instanceof String) && !(value instanceof List<?>)) errors.add(
                    reference.origin() + ": missing leaf " + reference.key() + " in " + language);
        }
        assertTrue(errors.isEmpty(), () -> String.join("\n", new TreeSet<>(errors)));
        assertTrue(references.size() > 500, "The audit must scan real call sites, not only dynamic fixtures");
    }

    private static boolean isTranslationGet(MethodInvocationTree call) {
        return method(call).equals("get") && call.getMethodSelect().toString().matches(
                ".*(?:LangHelper|LanguageManager|languageManager|\\blm)\\.get");
    }

    private static void resourceReferences(Object value, String path, String owner, String origin,
                                           Set<String> namespaces, List<Reference> references) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) {
            section.getValues(false).forEach((key, child) -> resourceReferences(child, key, owner, origin, namespaces, references));
        } else if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> resourceReferences(child, key.toString(), owner, origin, namespaces, references));
        } else if (value instanceof Iterable<?> list) {
            list.forEach(child -> resourceReferences(child, path, owner, origin, namespaces, references));
        } else if (value instanceof String candidate && KEY.matcher(candidate).matches()
                && (path.endsWith("-key") || namespaces.contains(candidate.substring(0, candidate.indexOf('.'))))) {
            references.add(new Reference(owner, candidate, origin));
        }
    }

    private static String method(MethodInvocationTree call) {
        return call.getMethodSelect() instanceof MemberSelectTree select ? select.getIdentifier().toString() : call.getMethodSelect().toString();
    }
}
