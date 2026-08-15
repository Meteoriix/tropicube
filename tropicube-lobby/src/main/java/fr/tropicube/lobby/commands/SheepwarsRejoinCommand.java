package fr.tropicube.lobby.commands;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Reconnects a player to the SheepWars instance they just left voluntarily. */
public class SheepwarsRejoinCommand implements CommandExecutor {

    private final TropicubeLobby plugin;

    public SheepwarsRejoinCommand(TropicubeLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!acceptsArguments(args)) {
            return false;
        }

        String instanceId = plugin.getPlayerLobbyListener().removeRejoinTarget(player.getUniqueId());
        if (instanceId == null || !plugin.getRedisManager().exists("sw:game-started:" + instanceId)) {
            player.sendMessage(LangHelper.component(player, "lobby.sw-rejoin-unavailable"));
            return true;
        }

        var instance = plugin.getRedisManager().getInstance(instanceId);
        if (instance == null || instance.getServerName() == null) {
            player.sendMessage(LangHelper.component(player, "lobby.sw-rejoin-unavailable"));
            return true;
        }

        player.sendMessage(LangHelper.component(player, "lobby.connect", instance.getServerName()));
        plugin.getLobbyServerManager().connectToServer(player, instance.getServerName());
        return true;
    }

    static boolean acceptsArguments(String[] args) {
        return args.length == 0;
    }
}
