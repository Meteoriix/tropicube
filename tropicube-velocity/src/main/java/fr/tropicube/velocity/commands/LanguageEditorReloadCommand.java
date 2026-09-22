package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.velocity.TropicubeVelocity;
import fr.tropicube.velocity.managers.VelocityLanguageManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.regex.Pattern;

/** Internal console-only bridge used by the local language editor after synchronizing files. */
public final class LanguageEditorReloadCommand implements SimpleCommand {
    private static final Pattern REQUEST_ID = Pattern.compile("[a-f0-9]{32}");
    private final TropicubeVelocity plugin;
    private final ProxyServer server;
    private final VelocityLanguageManager languages;
    private final Path dataDirectory;
    private final Logger logger;

    public LanguageEditorReloadCommand(TropicubeVelocity plugin, ProxyServer server,
                                       VelocityLanguageManager languages, Path dataDirectory, Logger logger) {
        this.plugin = plugin;
        this.server = server;
        this.languages = languages;
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (source instanceof Player) return;
        String[] arguments = invocation.arguments();
        if (arguments.length != 1 || !REQUEST_ID.matcher(arguments[0]).matches()) {
            source.sendMessage(net.kyori.adventure.text.Component.text("Identifiant de rechargement invalide."));
            return;
        }
        String requestId = arguments[0];
        server.getScheduler().buildTask(plugin, () -> {
            RuntimeException failure = null;
            try {
                languages.reload();
            } catch (RuntimeException reloadFailure) {
                failure = reloadFailure;
                logger.error("TROPICUBE > LANG > Rechargement refusé : {}", readableMessage(reloadFailure));
            }
            complete(requestId, failure);
        }).schedule();
        source.sendMessage(net.kyori.adventure.text.Component.text("Rechargement des langues programmé."));
    }

    private void complete(String requestId, RuntimeException failure) {
        Path target = dataDirectory.resolve(".language-editor-reload-" + requestId + ".status");
        Path temporary = target.resolveSibling(target.getFileName() + ".next");
        String status = failure == null ? "ok\n" : "error:" + Base64.getEncoder().encodeToString(
                readableMessage(failure).getBytes(StandardCharsets.UTF_8)) + "\n";
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temporary, status, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException writeFailure) {
            logger.error("TROPICUBE > LANG > Confirmation de rechargement impossible", writeFailure);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Le fichier temporaire sera remplacé lors d'une nouvelle tentative portant le même identifiant.
            }
        }
    }

    private static String readableMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
