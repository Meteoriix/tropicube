package fr.tropicube.core.util;

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
        plugin.getDatabaseManager().supplyAsync(work).whenComplete((result, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.SEVERE, "Échec d'une commande asynchrone", error);
                        sender.sendMessage(plugin.getLanguageManager()
                                .getComponentForLang(language, "general.operation-failed"));
                        return;
                    }
                    success.accept(result);
                }));
    }
}
