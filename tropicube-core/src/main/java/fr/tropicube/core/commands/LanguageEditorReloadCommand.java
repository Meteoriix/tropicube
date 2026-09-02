package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.ui.UiReloadParticipant;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Internal console-only bridge used by the local language editor after synchronizing files. */
public final class LanguageEditorReloadCommand implements CommandExecutor {
    private final TropicubeCore plugin;

    public LanguageEditorReloadCommand(TropicubeCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (sender instanceof Player) return true;
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
                    participants.forEach(UiReloadParticipant::refreshViewers);
                    plugin.getLogger().info("TROPICUBE > LANG > Langues et interfaces rechargées par l'éditeur local.");
                });
            } catch (RuntimeException failure) {
                plugin.getLogger().severe("TROPICUBE > UI > Rechargement refusé : " + failure.getMessage());
            }
        });
        sender.sendMessage("Rechargement des langues et interfaces programmé.");
        return true;
    }
}
