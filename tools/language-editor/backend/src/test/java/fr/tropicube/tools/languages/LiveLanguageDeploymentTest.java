package fr.tropicube.tools.languages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
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
        assertEquals(2, commands.stream().filter(LiveLanguageDeploymentTest::isReloadCommand).count());
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

    @Test
    void copiesUiManifestsBeforeOneReloadPerContainer() throws Exception {
        Path source = repository.resolve("tropicube-lobby/src/main/resources/menus.yml");
        Path fallenKingdoms = repository.resolve("tropicube-fallenkingdoms/src/main/resources/scoreboards.yml");
        Files.createDirectories(source.getParent());
        Files.createDirectories(fallenKingdoms.getParent());
        Files.writeString(source, "version: 1\nmenus: {}\n");
        Files.writeString(fallenKingdoms, "version: 1\nscoreboards: {}\n");
        List<List<String>> commands = new ArrayList<>();
        LiveLanguageDeployment deployment = new LiveLanguageDeployment(repository, (command, _, _) -> {
            commands.add(List.copyOf(command));
            if (command.contains("version")) return new LiveLanguageDeployment.CommandResult(0, "29.0");
            if (command.contains("ps")) return new LiveLanguageDeployment.CommandResult(0, "paper-one\n");
            return new LiveLanguageDeployment.CommandResult(0, "ok");
        }, "docker-test");

        var result = deployment.deployUi(List.of(
                new UiFiles.UiSnapshot("tropicube-lobby:menus.yml", "tropicube-lobby", "menus",
                        "tropicube-lobby/src/main/resources/menus.yml", null, Files.readString(source), "hash"),
                new UiFiles.UiSnapshot("tropicube-fallenkingdoms:scoreboards.yml", "tropicube-fallenkingdoms",
                        "scoreboards", "tropicube-fallenkingdoms/src/main/resources/scoreboards.yml", null,
                        Files.readString(fallenKingdoms), "hash")));

        assertEquals(1, result.updatedContainers());
        assertEquals(2, commands.stream().filter(command -> command.contains("cp")).count());
        assertTrue(commands.stream().flatMap(List::stream)
                .anyMatch(argument -> argument.contains("/data/plugins/TropicubeFallenKingdoms")));
        assertEquals(1, commands.stream().filter(LiveLanguageDeploymentTest::isReloadCommand).count());
    }

    @Test
    void countsOnlyContainersWhoseReloadWasConfirmed() throws Exception {
        Path source = repository.resolve("tropicube-core/src/main/resources/languages");
        Files.createDirectories(source);
        for (String language : LanguageFiles.LANGUAGES) Files.writeString(source.resolve(language + ".yml"), "message: ok\n");
        String encodedFailure = Base64.getEncoder().encodeToString(
                "catalogue invalide".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        LiveLanguageDeployment deployment = new LiveLanguageDeployment(repository, (command, _, _) -> {
            if (command.contains("version")) return new LiveLanguageDeployment.CommandResult(0, "29.0");
            if (command.contains("ps")) return new LiveLanguageDeployment.CommandResult(0, "paper-one\npaper-two\n");
            if (isConfirmationWait(command) && command.contains("paper-two")) {
                return new LiveLanguageDeployment.CommandResult(0, "error:" + encodedFailure);
            }
            return new LiveLanguageDeployment.CommandResult(0, "ok");
        }, "docker-test");

        var result = deployment.deploy(new LanguageFiles.LanguageSet("tropicube-core",
                "tropicube-core/src/main/resources/languages", null));

        assertEquals(1, result.updatedContainers());
        assertEquals(List.of("paper-one"), result.containers());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().contains("catalogue invalide"));
    }

    @Test
    void reportsMissingReloadConfirmationAsAnError() throws Exception {
        Path source = repository.resolve("tropicube-core/src/main/resources/languages");
        Files.createDirectories(source);
        for (String language : LanguageFiles.LANGUAGES) Files.writeString(source.resolve(language + ".yml"), "message: ok\n");
        LiveLanguageDeployment deployment = new LiveLanguageDeployment(repository, (command, _, _) -> {
            if (command.contains("version")) return new LiveLanguageDeployment.CommandResult(0, "29.0");
            if (command.contains("ps")) return new LiveLanguageDeployment.CommandResult(0, "paper-one\n");
            if (isConfirmationWait(command)) {
                return new LiveLanguageDeployment.CommandResult(124, "délai de rechargement dépassé");
            }
            return new LiveLanguageDeployment.CommandResult(0, "ok");
        }, "docker-test");

        var result = deployment.deploy(new LanguageFiles.LanguageSet("tropicube-core",
                "tropicube-core/src/main/resources/languages", null));

        assertEquals(0, result.updatedContainers());
        assertTrue(result.errors().getFirst().contains("délai de rechargement dépassé"));
    }

    private static boolean isReloadCommand(List<String> command) {
        return command.stream().anyMatch(argument -> argument.startsWith("languageeditorreload "));
    }

    private static boolean isConfirmationWait(List<String> command) {
        return command.stream().anyMatch(argument -> argument.contains("délai de rechargement dépassé"));
    }
}
