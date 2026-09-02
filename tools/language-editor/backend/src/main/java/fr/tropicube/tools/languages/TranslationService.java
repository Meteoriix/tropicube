package fr.tropicube.tools.languages;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Calls LibreTranslate while keeping MiniMessage and project tokens outside translated segments. */
final class TranslationService {
    private static final Pattern PROTECTED = Pattern.compile(
            "(?<!\\\\)<[^<>]+>|\\\\<[^<>]+>|\\{(?:[a-z][a-z0-9_]*|\\d+)}|/[a-zA-Z0-9_:-]+|"
                    + "[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+|\\p{S}[\\p{M}\\u200D\\p{S}]*");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Gson gson = new Gson();
    private final URI endpoint;
    private final String apiKey;

    TranslationService() {
        String base = System.getenv().getOrDefault("LIBRETRANSLATE_URL", "http://127.0.0.1:5000");
        endpoint = URI.create(base.replaceFirst("/$", "") + "/translate");
        apiKey = System.getenv().getOrDefault("LIBRETRANSLATE_API_KEY", "");
    }

    TranslationService(URI endpoint, String apiKey) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
    }

    boolean available() {
        try {
            URI languages = URI.create(endpoint.toString().replaceFirst("/translate$", "/languages"));
            return client.send(HttpRequest.newBuilder(languages).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception ignored) {
            return false;
        }
    }

    String translate(String text, String target, Map<String, String> glossary) throws IOException, InterruptedException {
        List<Token> tokens = protectedTokens(text, glossary);
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        for (Token token : tokens) {
            if (token.start() > cursor) result.append(translateSegment(text.substring(cursor, token.start()), target));
            result.append(token.replacement());
            cursor = token.end();
        }
        if (cursor < text.length()) result.append(translateSegment(text.substring(cursor), target));
        return result.toString();
    }

    private List<Token> protectedTokens(String text, Map<String, String> glossary) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = PROTECTED.matcher(text);
        while (matcher.find()) tokens.add(new Token(matcher.start(), matcher.end(), matcher.group()));
        glossary.forEach((source, target) -> {
            int from = 0;
            while ((from = text.indexOf(source, from)) >= 0) {
                tokens.add(new Token(from, from + source.length(), target));
                from += source.length();
            }
        });
        tokens.sort(Comparator.comparingInt(Token::start).thenComparing(Comparator.comparingInt(Token::end).reversed()));
        List<Token> nonOverlapping = new ArrayList<>();
        int end = -1;
        for (Token token : tokens) {
            if (token.start() >= end) {
                nonOverlapping.add(token);
                end = token.end();
            }
        }
        return nonOverlapping;
    }

    private String translateSegment(String segment, String target) throws IOException, InterruptedException {
        if (segment.isBlank()) return segment;
        int leading = 0;
        while (leading < segment.length() && Character.isWhitespace(segment.charAt(leading))) leading++;
        int trailing = segment.length();
        while (trailing > leading && Character.isWhitespace(segment.charAt(trailing - 1))) trailing--;
        String content = segment.substring(leading, trailing);
        JsonObject request = new JsonObject();
        request.addProperty("q", content);
        request.addProperty("source", "fr");
        request.addProperty("target", target);
        request.addProperty("format", "text");
        if (!apiKey.isBlank()) request.addProperty("api_key", apiKey);
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                .build();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException("LibreTranslate HTTP " + response.statusCode());
        JsonObject body = gson.fromJson(response.body(), JsonObject.class);
        if (body == null || !body.has("translatedText")) throw new IOException("Réponse LibreTranslate invalide");
        return segment.substring(0, leading) + body.get("translatedText").getAsString() + segment.substring(trailing);
    }

    private record Token(int start, int end, String replacement) {}
}
