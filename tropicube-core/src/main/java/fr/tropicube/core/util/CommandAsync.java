package fr.tropicube.core.util;

import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.DatabaseManager;
import org.bukkit.command.CommandSender;

import java.util.function.Consumer;
import java.util.logging.Level;

/** Runs blocking command work away from Paper's main thread. */
public final class CommandAsync {
    private CommandAsync() {}

    public static <T> void run(TropicubeCore plugin, CommandSender sender, String language,
                               DatabaseManager.SqlSupplier<T> work, Consumer<T> success) {
        plugin.getDatabaseManager().supplyAsync(work).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled()) return;
                    if (error != null) {
                        plugin.getLogger().log(Level.SEVERE, MessageStyle.log("tc", "COMMAND_ASYNC", "<red>Échec d'une commande asynchrone"), error);
                        sender.sendMessage(plugin.getLanguageManager()
                                .getComponentForLang(language, "general.operation-failed"));
                        return;
                    }
                    success.accept(result);
                });
            } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) { /* Plugin stopped during completion. */ }
        });
    }
}
