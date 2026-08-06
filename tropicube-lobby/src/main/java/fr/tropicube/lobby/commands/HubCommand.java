package fr.tropicube.lobby.commands;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

// /hub — revient au spawn du lobby
/** Informe le joueur qu'il se trouve déjà sur un lobby. */
public class HubCommand implements CommandExecutor {
    private final TropicubeLobby plugin;
    public HubCommand(TropicubeLobby plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangHelper.component((UUID) null, "general.player-only"));
            return true;
        }
        if (!plugin.getPlayerLobbyListener().teleportToSpawn(player)) {
            player.sendMessage(LangHelper.component(player, "lobby.spawn-not-configured"));
            return true;
        }
        player.sendMessage(LangHelper.component(player, "lobby.hub-teleported"));
        return true;
    }
}
