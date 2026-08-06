package fr.tropicube.lobby.commands;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/** Active ou désactive le vol permanent réservé au personnel du lobby. */
public class FlyModeCommand implements CommandExecutor {

    private final TropicubeLobby plugin;

    public FlyModeCommand(TropicubeLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangHelper.component((UUID) null, "general.player-only"));
            return true;
        }
        if (!player.hasPermission("tropicube.lobby.fly")) {
            player.sendMessage(LangHelper.component(player, "general.no-permission"));
            return true;
        }
        boolean flyEnabled = plugin.getPlayerLobbyListener().toggleFlyMode(player);
        if (flyEnabled) {
            player.sendMessage(LangHelper.component(player, "lobby.flymode-enabled"));
        } else {
            player.sendMessage(LangHelper.component(player, "lobby.flymode-disabled"));
        }
        return true;
    }
}
