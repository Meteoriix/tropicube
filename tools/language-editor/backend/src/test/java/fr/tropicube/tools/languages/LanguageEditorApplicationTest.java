package fr.tropicube.tools.languages;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageEditorApplicationTest {
    @TempDir
    Path repository;

    @Test
    void staticResponseCompletesWithItsDeclaredBody() throws Exception {
        Path distribution = repository.resolve("tools/language-editor/frontend/dist");
        Files.createDirectories(distribution);
        Files.writeString(distribution.resolve("index.html"), "<html>éditeur</html>");

        HttpServer server = LanguageEditorApplication.createServer(repository, 0);
        server.start();
        try {
            URI address = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(address).timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("<html>éditeur</html>", response.body());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void previewRendersFormattedScoreboardTitleAndEscapesPlaceholderValues() throws Exception {
        HttpServer server = LanguageEditorApplication.createServer(repository, 0);
        server.start();
        try {
            URI address = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/preview");
            HttpRequest request = HttpRequest.newBuilder(address).timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"message":"<gold><bold>{server}</bold></gold>","placeholders":{"server":"<red>Tropicube"}}
                            """))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"color\":\"gold\""));
            assertTrue(response.body().contains("\"bold\":true"));
            assertTrue(response.body().contains("Tropicube"));
            assertTrue(!response.body().contains("\"color\":\"red\""));
        } finally {
            server.stop(0);
        }
    }
}
