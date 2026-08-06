package fr.tropicube.lobby.commands;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// /servers — ouvre le sélecteur de serveurs
/** Ouvre le sélecteur des instances de jeu disponibles. */
public class ServersCommand implements CommandExecutor {
    private final TropicubeLobby plugin;
    public ServersCommand(TropicubeLobby plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangHelper.component((java.util.UUID) null, "general.player-only"));
            return true;
        }
        plugin.getGuiManager().openServerTypeSelector(player);
        return true;
    }
}
