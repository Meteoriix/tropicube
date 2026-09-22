package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.ui.UiReloadParticipant;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.regex.Pattern;

/** Internal console-only bridge used by the local language editor after synchronizing files. */
public final class LanguageEditorReloadCommand implements CommandExecutor {
    private static final Pattern REQUEST_ID = Pattern.compile("[a-f0-9]{32}");
    private final TropicubeCore plugin;

    public LanguageEditorReloadCommand(TropicubeCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (sender instanceof Player) return true;
        if (arguments.length != 1 || !REQUEST_ID.matcher(arguments[0]).matches()) {
            sender.sendMessage("Identifiant de rechargement invalide.");
            return true;
        }
        String requestId = arguments[0];
        var participants = Bukkit.getServicesManager().getRegistrations(UiReloadParticipant.class).stream()
                .map(registration -> registration.getProvider())
                .toList();
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                var commits = participants.stream().map(UiReloadParticipant::prepareReload).toList();
                plugin.getLanguageManager().reloadFiles();
                commits.forEach(Runnable::run);
                plugin.getRuntimeUiBundle().publish();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        participants.forEach(UiReloadParticipant::refreshViewers);
                        plugin.getLogger().info("TROPICUBE > LANG > Langues et interfaces rechargées par l'éditeur local.");
                        completeAsync(requestId, null);
                    } catch (RuntimeException failure) {
                        plugin.getLogger().severe("TROPICUBE > UI > Rafraîchissement refusé : " + failure.getMessage());
                        completeAsync(requestId, failure);
                    }
                });
            } catch (RuntimeException failure) {
                plugin.getLogger().severe("TROPICUBE > UI > Rechargement refusé : " + failure.getMessage());
                complete(requestId, failure);
            }
        });
        sender.sendMessage("Rechargement des langues et interfaces programmé.");
        return true;
    }

    private void completeAsync(String requestId, RuntimeException failure) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> complete(requestId, failure));
    }

    private void complete(String requestId, RuntimeException failure) {
        Path target = plugin.getDataFolder().toPath().resolve(".language-editor-reload-" + requestId + ".status");
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
            plugin.getLogger().severe("TROPICUBE > UI > Confirmation de rechargement impossible : "
                    + writeFailure.getMessage());
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
