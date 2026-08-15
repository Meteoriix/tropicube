package fr.tropicube.lobby.commands;

import fr.tropicube.lobby.TropicubeLobby;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Confirms a fresh five-game automatic replay batch. */
public final class ReplayConfirmCommand implements CommandExecutor {
    private final TropicubeLobby plugin;

    public ReplayConfirmCommand(TropicubeLobby plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        var playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getRedisManager().setAutoReplay(playerId, true, plugin.getAutoReplayBatchSize());
            plugin.getRedisManager().consumeAutoReplay(playerId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online != null) online.performCommand("replay");
            });
        });
        return true;
    }
}
