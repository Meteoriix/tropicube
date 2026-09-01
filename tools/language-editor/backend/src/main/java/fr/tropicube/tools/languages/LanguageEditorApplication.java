package fr.tropicube.tools.languages;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** Local-only HTTP entry point for the Tropicube language editor. */
public final class LanguageEditorApplication {
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    private final Gson gson = new Gson();
    private final Path repository;
    private final LanguageFiles files;
    private final TranslationService translations = new TranslationService();
    private final MiniMessage miniMessage;

    private LanguageEditorApplication(Path repository) {
        this.repository = repository;
        this.files = new LanguageFiles(repository);
        Component network = prefix("TROPICUBE", NamedTextColor.GOLD);
        Component sheepwars = prefix("SHEEPWARS", NamedTextColor.AQUA);
        this.miniMessage = MiniMessage.builder().tags(TagResolver.builder()
                .resolver(StandardTags.defaults())
                .tag("tc", Tag.inserting(network))
                .tag("sw", Tag.inserting(sheepwars))
                .build()).build();
    }

    public static void main(String[] arguments) throws Exception {
        Path repository = findRepository(Path.of(System.getProperty("user.dir")));
        int port = Integer.parseInt(System.getenv().getOrDefault("TROPICUBE_LANGUAGE_EDITOR_PORT", "8765"));
        LanguageEditorApplication application = new LanguageEditorApplication(repository);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/", application::api);
        server.createContext("/", application::staticFile);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        URI address = URI.create("http://127.0.0.1:" + port);
        System.out.println("Éditeur de langues Tropicube : " + address);
        if (!List.of(arguments).contains("--no-browser") && Desktop.isDesktopSupported()) {
            try { Desktop.getDesktop().browse(address); } catch (IOException ignored) { }
        }
    }

    private void api(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if (method.equals("GET") && path.equals("/api/state")) state(exchange);
            else if (method.equals("GET") && path.equals("/api/translation/status")) json(exchange, 200,
                    Map.of("available", translations.available(), "url",
                            System.getenv().getOrDefault("LIBRETRANSLATE_URL", "http://127.0.0.1:5000")));
            else if (method.equals("GET") && path.equals("/api/usages")) usages(exchange);
            else if (method.equals("POST") && path.equals("/api/preview")) preview(exchange);
            else if (method.equals("POST") && path.equals("/api/translate")) translate(exchange);
            else if (method.equals("POST") && path.equals("/api/validate")) validate(exchange);
            else if (method.equals("POST") && path.equals("/api/apply")) apply(exchange);
            else if (method.equals("POST") && path.equals("/api/catalog")) saveCatalog(exchange);
            else json(exchange, 404, Map.of("error", "Route inconnue"));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            json(exchange, 409, Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            exception.printStackTrace();
            json(exchange, 500, Map.of("error", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void state(HttpExchange exchange) throws IOException {
        List<?> sets = files.discover().stream().map(set -> {
            try {
                return Map.of("set", set, "files", files.read(set));
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }).toList();
        Path catalog = repository.resolve("tools/language-editor/catalog.yml");
        json(exchange, 200, Map.of("sets", sets, "catalog",
                Files.exists(catalog) ? Files.readString(catalog) : "version: 1\n"));
    }

    private void preview(HttpExchange exchange) throws IOException {
        JsonObject request = body(exchange);
        String message = request.get("message").getAsString();
        if (request.has("placeholders")) {
            for (Map.Entry<String, JsonElement> entry : request.getAsJsonObject("placeholders").entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", miniMessage.escapeTags(entry.getValue().getAsString()));
            }
        }
        String serialized = GsonComponentSerializer.gson().serialize(miniMessage.deserialize(message));
        json(exchange, 200, JsonParser.parseString(serialized));
    }

    private void translate(HttpExchange exchange) throws Exception {
        JsonObject request = body(exchange);
        String target = request.get("target").getAsString();
        if (!List.of("en", "de", "es").contains(target)) throw new IllegalArgumentException("Langue cible invalide");
        Map<String, String> glossary = new LinkedHashMap<>();
        if (request.has("glossary")) request.getAsJsonObject("glossary").entrySet()
                .forEach(entry -> glossary.put(entry.getKey(), entry.getValue().getAsString()));
        String translated = translations.translate(request.get("text").getAsString(), target, glossary);
        json(exchange, 200, Map.of("translatedText", translated));
    }

    private void validate(HttpExchange exchange) throws IOException {
        JsonObject request = body(exchange);
        json(exchange, 200, Map.of("diagnostics", files.validate(stringMap(request.getAsJsonObject("documents")))));
    }

    private void apply(HttpExchange exchange) throws IOException {
        JsonObject request = body(exchange);
        LanguageFiles.LanguageSet set = gson.fromJson(request.get("set"), LanguageFiles.LanguageSet.class);
        files.apply(set, stringMap(request.getAsJsonObject("expectedHashes")),
                stringMap(request.getAsJsonObject("documents")));
        json(exchange, 200, Map.of("ok", true, "files", files.read(set)));
    }

    private void usages(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String key = query == null ? "" : URLDecoder.decode(query.replaceFirst("^key=", ""), StandardCharsets.UTF_8);
        if (key.isBlank()) throw new IllegalArgumentException("Clé requise");
        List<Map<String, Object>> matches;
        try (var paths = Files.walk(repository)) {
            matches = paths.filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(repository.resolve(".git")) && !path.toString().contains("target")
                            && !path.toString().contains("node_modules"))
                    .filter(path -> List.of(".java", ".yml", ".yaml", ".md").stream()
                            .anyMatch(suffix -> path.toString().endsWith(suffix)))
                    .flatMap(path -> {
                        try {
                            List<String> lines = Files.readAllLines(path);
                            return java.util.stream.IntStream.range(0, lines.size())
                                    .filter(index -> lines.get(index).contains(key))
                                    .mapToObj(index -> Map.<String, Object>of(
                                            "file", repository.relativize(path).toString().replace('\\', '/'),
                                            "line", index + 1, "text", lines.get(index).trim()));
                        } catch (IOException ignored) { return java.util.stream.Stream.empty(); }
                    }).toList();
        }
        json(exchange, 200, Map.of("usages", matches));
    }

    private void saveCatalog(HttpExchange exchange) throws IOException {
        String content = body(exchange).get("content").getAsString();
        Object parsed = LanguageFiles.safeYaml().load(content);
        if (!(parsed instanceof Map<?, ?>)) throw new IllegalArgumentException("Le catalogue doit être un objet YAML");
        Path target = repository.resolve("tools/language-editor/catalog.yml");
        Path temporary = Files.createTempFile(target.getParent(), ".catalog-", ".tmp");
        try {
            Files.writeString(temporary, content.endsWith("\n") ? content : content + "\n", StandardCharsets.UTF_8);
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        json(exchange, 200, Map.of("ok", true));
    }

    private void staticFile(HttpExchange exchange) throws IOException {
        String requested = exchange.getRequestURI().getPath();
        if (requested.equals("/")) requested = "/index.html";
        Path root = repository.resolve("tools/language-editor/frontend/dist").normalize();
        Path file = root.resolve(requested.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            file = root.resolve("index.html");
        }
        if (!Files.isRegularFile(file)) {
            text(exchange, 503, "Interface non construite. Lancez npm --prefix tools/language-editor/frontend run build.", "text/plain; charset=utf-8");
            return;
        }
        String type = file.toString().endsWith(".js") ? "text/javascript" : file.toString().endsWith(".css")
                ? "text/css" : "text/html; charset=utf-8";
        byte[] content = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content);
    }

    private JsonObject body(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new IllegalArgumentException("Requête trop volumineuse");
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private Map<String, String> stringMap(JsonObject object) {
        Map<String, String> result = new LinkedHashMap<>();
        object.entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
        return result;
    }

    private void json(HttpExchange exchange, int status, Object value) throws IOException {
        text(exchange, status, gson.toJson(value), "application/json; charset=utf-8");
    }

    private void text(HttpExchange exchange, int status, String value, String type) throws IOException {
        byte[] content = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
    }

    private static Component prefix(String name, NamedTextColor color) {
        return Component.text().decoration(TextDecoration.BOLD, false)
                .append(Component.text(name, color, TextDecoration.BOLD))
                .append(Component.text(" > ", NamedTextColor.DARK_GRAY)).build();
    }

    private static Path findRepository(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (current != null && !Files.isDirectory(current.resolve(".git"))) current = current.getParent();
        if (current == null) throw new IllegalStateException("Exécutez l'éditeur depuis le dépôt Tropicube");
        return current;
    }
}
