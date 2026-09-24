package fr.tropicube.tools.languages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Copies validated translations to running Tropicube containers and reloads them through local RCON. */
final class LiveLanguageDeployment {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final int RELOAD_WAIT_SECONDS = 20;
    private static final int MAX_OUTPUT_CHARACTERS = 16_384;

    private final Path repository;
    private final CommandRunner runner;
    private final String dockerCommand;

    LiveLanguageDeployment(Path repository) {
        this(repository, new ProcessCommandRunner(),
                System.getenv().getOrDefault("TROPICUBE_DOCKER_COMMAND", "docker"));
    }

    LiveLanguageDeployment(Path repository, CommandRunner runner, String dockerCommand) {
        this.repository = repository.toAbsolutePath().normalize();
        this.runner = runner;
        this.dockerCommand = dockerCommand;
    }

    LiveStatus status() {
        CommandResult result = run(List.of(dockerCommand, "version", "--format", "{{.Server.Version}}"));
        return new LiveStatus(result.success(), result.success()
                ? "Docker disponible"
                : "Docker indisponible : " + result.summary());
    }

    LiveResult deploy(LanguageFiles.LanguageSet set) {
        Target target = Target.forSet(set.id());
        if (target == null) {
            return new LiveResult(false, 0, List.of(),
                    List.of("Le module " + set.id() + " ne possède pas de cible de jeu live"));
        }
        LiveStatus status = status();
        if (!status.available()) return new LiveResult(false, 0, List.of(), List.of(status.message()));

        CommandResult listed = run(target.listCommand(dockerCommand));
        if (!listed.success()) {
            return new LiveResult(true, 0, List.of(),
                    List.of("Impossible de lister les conteneurs : " + listed.summary()));
        }

        List<String> containers = listed.output().lines().map(String::trim)
                .filter(name -> !name.isEmpty()).distinct().toList();
        if (containers.isEmpty()) {
            return new LiveResult(true, 0, List.of(), List.of("Aucun conteneur actif pour " + set.id()));
        }

        Path sourceDirectory = resolve(set.sourceDirectory());
        List<String> updated = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String container : containers) {
            try {
                synchronize(container, sourceDirectory, target);
                updated.add(container);
            } catch (IllegalStateException failure) {
                errors.add(container + " : " + failure.getMessage());
            }
        }
        return new LiveResult(true, updated.size(), List.copyOf(updated), List.copyOf(errors));
    }

    /** Installs all UI manifests on active Paper instances and triggers one coherent reload. */
    LiveResult deployUi(Iterable<UiFiles.UiSnapshot> snapshots) {
        LiveStatus status = status();
        if (!status.available()) return new LiveResult(false, 0, List.of(), List.of(status.message()));
        Target paper = Target.forSet("tropicube-core");
        CommandResult listed = run(paper.listCommand(dockerCommand));
        if (!listed.success()) return new LiveResult(true, 0, List.of(),
                List.of("Impossible de lister les conteneurs : " + listed.summary()));
        List<String> containers = listed.output().lines().map(String::trim)
                .filter(name -> !name.isEmpty()).distinct().toList();
        List<String> updated = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String container : containers) {
            String token = UUID.randomUUID().toString().replace("-", "");
            List<String> temporaryFiles = new ArrayList<>();
            String completionFile = paper.completionFile(token);
            try {
                remove(container, completionFile);
                for (UiFiles.UiSnapshot snapshot : snapshots) {
                    String temporary = "/tmp/tropicube-ui-" + token + "-" + snapshot.module()
                            + "-" + snapshot.type() + ".yml";
                    temporaryFiles.add(temporary);
                    requireSuccess(run(List.of(dockerCommand, "cp", resolve(snapshot.sourcePath()).toString(),
                            container + ":" + temporary)), "copie de " + snapshot.id());
                    String pluginDirectory = switch (snapshot.module()) {
                        case "tropicube-core" -> "/data/plugins/TropicubeCore";
                        case "tropicube-lobby" -> "/data/plugins/TropicubeLobby";
                        case "tropicube-sheepwars" -> "/data/plugins/TropicubeSheepwars";
                        case "tropicube-fallenkingdoms" -> "/data/plugins/TropicubeFallenKingdoms";
                        default -> throw new IllegalArgumentException("Module UI non déployable : " + snapshot.module());
                    };
                    String target = pluginDirectory + "/" + snapshot.type() + ".yml";
                    requireSuccess(run(List.of(dockerCommand, "exec", container, "sh", "-c",
                            "set -eu; mkdir -p '" + pluginDirectory + "'; cp '" + temporary + "' '"
                                    + target + ".next'; mv '" + target + ".next' '" + target + "'")),
                            "installation de " + snapshot.id());
                }
                requireSuccess(run(List.of(dockerCommand, "exec", container, "rcon-cli",
                                "languageeditorreload " + token)),
                        "rechargement en jeu");
                awaitReload(container, completionFile);
                updated.add(container);
            } catch (IllegalStateException failure) {
                errors.add(container + " : " + failure.getMessage());
            } finally {
                for (String temporary : temporaryFiles) {
                    run(List.of(dockerCommand, "exec", container, "rm", "-f", temporary));
                }
                remove(container, completionFile);
            }
        }
        return new LiveResult(true, updated.size(), List.copyOf(updated), List.copyOf(errors));
    }

    private void synchronize(String container, Path sourceDirectory, Target target) {
        String token = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        String prefix = "/tmp/tropicube-language-editor-" + token;
        String completionFile = target.completionFile(token);
        try {
            remove(container, completionFile);
            for (String language : LanguageFiles.LANGUAGES) {
                Path source = sourceDirectory.resolve(language + ".yml");
                requireSuccess(run(List.of(dockerCommand, "cp", source.toString(),
                        container + ":" + prefix + "-" + language + ".yml")), "copie de " + language + ".yml");
            }
            requireSuccess(run(List.of(dockerCommand, "exec", container, "sh", "-c",
                    installScript(prefix, target.directory()))), "installation atomique des langues");
            requireSuccess(run(List.of(dockerCommand, "exec", container, "rcon-cli",
                            "languageeditorreload " + token)),
                    "rechargement en jeu");
            awaitReload(container, completionFile);
        } finally {
            run(List.of(dockerCommand, "exec", container, "sh", "-c", "rm -f " + prefix + "-*.yml"));
            remove(container, completionFile);
        }
    }

    private void awaitReload(String container, String completionFile) {
        String script = "i=0; while [ ! -f '" + completionFile + "' ] && [ \"$i\" -lt "
                + RELOAD_WAIT_SECONDS + " ]; do i=$((i+1)); sleep 1; done; "
                + "[ -f '" + completionFile + "' ] || { echo 'délai de rechargement dépassé'; exit 124; }; "
                + "cat '" + completionFile + "'";
        CommandResult result = run(List.of(dockerCommand, "exec", container, "sh", "-c", script));
        requireSuccess(result, "confirmation du rechargement");
        String status = result.output() == null ? "" : result.output().strip();
        if (status.equals("ok")) return;
        if (status.startsWith("error:")) {
            String message;
            try {
                message = new String(Base64.getDecoder().decode(status.substring("error:".length())),
                        StandardCharsets.UTF_8);
            } catch (IllegalArgumentException invalidStatus) {
                throw new IllegalStateException("confirmation de rechargement invalide");
            }
            throw new IllegalStateException("rechargement refusé : " + message);
        }
        throw new IllegalStateException("confirmation de rechargement invalide : " + result.summary());
    }

    private void remove(String container, String path) {
        run(List.of(dockerCommand, "exec", container, "rm", "-f", path));
    }

    private static String installScript(String prefix, String directory) {
        StringBuilder script = new StringBuilder("set -eu; mkdir -p '").append(directory).append("'; ");
        for (String language : LanguageFiles.LANGUAGES) {
            script.append("cp '").append(prefix).append('-').append(language).append(".yml' '")
                    .append(directory).append('/').append(language).append(".yml.next'; ");
        }
        for (String language : LanguageFiles.LANGUAGES) {
            script.append("mv '").append(directory).append('/').append(language).append(".yml.next' '")
                    .append(directory).append('/').append(language).append(".yml'; ");
        }
        return script.toString();
    }

    private Path resolve(String relative) {
        Path resolved = repository.resolve(relative).normalize();
        if (!resolved.startsWith(repository)) throw new IllegalArgumentException("Chemin de langues hors dépôt");
        return resolved;
    }

    private CommandResult run(List<String> command) {
        try {
            return runner.run(command, repository, COMMAND_TIMEOUT);
        } catch (IOException exception) {
            return new CommandResult(-1, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "commande interrompue");
        }
    }

    private static void requireSuccess(CommandResult result, String operation) {
        if (!result.success()) throw new IllegalStateException(operation + " impossible : " + result.summary());
    }

    record LiveStatus(boolean available, String message) {}
    record LiveResult(boolean available, int updatedContainers, List<String> containers, List<String> errors) {}

    private record Target(String directory, String completionDirectory, List<String> filters) {
        static Target forSet(String id) {
            return switch (id) {
                case "tropicube-core" -> new Target("/data/plugins/TropicubeCore/languages",
                        "/data/plugins/TropicubeCore",
                        List.of("--filter", "label=fr.tropicube.dynamic=true", "--filter", "name=^/tropicube-"));
                case "tropicube-velocity" -> new Target("/server/plugins/tropicube-velocity/languages",
                        "/server/plugins/tropicube-velocity",
                        List.of("--filter", "name=^/tropicube-velocity$"));
                default -> null;
            };
        }

        List<String> listCommand(String dockerCommand) {
            List<String> command = new ArrayList<>(List.of(dockerCommand, "ps"));
            command.addAll(filters);
            command.addAll(List.of("--format", "{{.Names}}"));
            return command;
        }

        String completionFile(String token) {
            return completionDirectory + "/.language-editor-reload-" + token + ".status";
        }
    }

    interface CommandRunner {
        CommandResult run(List<String> command, Path workingDirectory, Duration timeout)
                throws IOException, InterruptedException;
    }

    record CommandResult(int exitCode, String output) {
        boolean success() { return exitCode == 0; }
        String summary() {
            String normalized = output == null ? "" : output.strip().replaceAll("\\s+", " ");
            if (normalized.length() > MAX_OUTPUT_CHARACTERS) normalized = normalized.substring(0, MAX_OUTPUT_CHARACTERS);
            return normalized.isEmpty() ? "code " + exitCode : normalized;
        }
    }

    private static final class ProcessCommandRunner implements CommandRunner {
        @Override
        public CommandResult run(List<String> command, Path workingDirectory, Duration timeout)
                throws IOException, InterruptedException {
            Process process = new ProcessBuilder(command).directory(workingDirectory.toFile())
                    .redirectErrorStream(true).start();
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor();
                return new CommandResult(-1, "délai de " + timeout.toSeconds() + " s dépassé");
            }
            byte[] bytes = process.getInputStream().readNBytes(MAX_OUTPUT_CHARACTERS * 4);
            return new CommandResult(process.exitValue(), new String(bytes, StandardCharsets.UTF_8));
        }
    }
}
