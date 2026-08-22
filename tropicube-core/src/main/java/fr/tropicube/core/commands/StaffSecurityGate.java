package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Central secondary-session gate for sensitive staff commands. */
final class StaffSecurityGate {
    private StaffSecurityGate() {}

    static boolean allow(TropicubeCore plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        if (plugin.getStaffSecurityService().hasSession(player.getUniqueId())) return true;
        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "staff.session-required"));
        return false;
    }
}
