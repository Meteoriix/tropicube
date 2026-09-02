package fr.tropicube.tools.languages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveLanguageDeploymentTest {
    @TempDir
    Path repository;

    @Test
    void copiesEveryLanguageAndReloadsEveryRunningPaperContainer() throws Exception {
        Path source = repository.resolve("tropicube-core/src/main/resources/languages");
        Files.createDirectories(source);
        for (String language : LanguageFiles.LANGUAGES) Files.writeString(source.resolve(language + ".yml"), "message: ok\n");
        List<List<String>> commands = new ArrayList<>();
        LiveLanguageDeployment.CommandRunner runner = (command, _, _) -> {
            commands.add(List.copyOf(command));
            if (command.contains("version")) return new LiveLanguageDeployment.CommandResult(0, "29.0");
            if (command.contains("ps")) return new LiveLanguageDeployment.CommandResult(0, "paper-one\npaper-two\n");
            return new LiveLanguageDeployment.CommandResult(0, "ok");
        };
        LiveLanguageDeployment deployment = new LiveLanguageDeployment(repository, runner, "docker-test");

        var result = deployment.deploy(new LanguageFiles.LanguageSet("tropicube-core",
                "tropicube-core/src/main/resources/languages", null));

        assertTrue(result.available());
        assertEquals(2, result.updatedContainers());
        assertEquals(8, commands.stream().filter(command -> command.contains("cp")).count());
        assertEquals(2, commands.stream().filter(command -> command.contains("languageeditorreload")).count());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void reportsDockerAsUnavailableWithoutAttemptingDeployment() {
        LiveLanguageDeployment deployment = new LiveLanguageDeployment(repository,
                (_, _, _) -> new LiveLanguageDeployment.CommandResult(-1, "daemon absent"), "docker-test");

        var result = deployment.deploy(new LanguageFiles.LanguageSet("tropicube-core", "languages", null));

        assertEquals(0, result.updatedContainers());
        assertEquals(List.of("Docker indisponible : daemon absent"), result.errors());
    }
}
