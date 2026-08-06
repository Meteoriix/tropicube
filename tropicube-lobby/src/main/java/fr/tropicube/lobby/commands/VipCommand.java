package fr.tropicube.lobby.commands;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// /vip — ouvre la boutique VIP
/** Ouvre la boutique de grades VIP du lobby. */
public class VipCommand implements CommandExecutor {
    private final TropicubeLobby plugin;
    public VipCommand(TropicubeLobby plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangHelper.component((java.util.UUID) null, "general.player-only"));
            return true;
        }
        plugin.getGuiManager().openVipShop(player);
        return true;
    }
}
