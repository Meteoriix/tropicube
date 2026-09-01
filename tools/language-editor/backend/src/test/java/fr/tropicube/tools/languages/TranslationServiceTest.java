package fr.tropicube.tools.languages;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationServiceTest {
    @Test
    void protectsMiniMessagePlaceholdersCommandsAndGlossary() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> requests = new ArrayList<>();
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/translate", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"translatedText\":\"translated\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/translate");
            String result = new TranslationService(endpoint, "").translate(
                    "<tc><green>Bonjour Tropicube {0}, utilise /play", "en", Map.of("Tropicube", "Tropicube"));
            assertEquals("<tc><green>translated Tropicube {0}translated /play", result);
            assertTrue(requests.stream().noneMatch(body -> body.contains("<tc>")
                    || body.contains("Tropicube") || body.contains("{0}") || body.contains("/play")));
        } finally {
            server.stop(0);
        }
    }
}
