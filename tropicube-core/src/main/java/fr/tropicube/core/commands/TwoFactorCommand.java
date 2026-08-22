package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.network.StaffSecurityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Console-assisted TOTP enrollment and secondary staff-session validation. */
public final class TwoFactorCommand implements CommandExecutor {
    private final TropicubeCore plugin;

    public TwoFactorCommand(TropicubeCore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, @NonNull Command command,
                                       @NonNull String label, String @NonNull [] args) {
        StaffSecurityService security = plugin.getStaffSecurityService();
        if (!security.available()) {
            sender.sendMessage(Component.text("TOTP indisponible : clé maîtresse non configurée.", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("issue")) {
            if (!sender.hasPermission("tropicube.2fa.issue")) return false;
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Le joueur doit être connecté.", NamedTextColor.RED));
                return true;
            }
            String token = security.issueEnrollment(target.getUniqueId());
            sender.sendMessage(Component.text("Jeton d'inscription pour " + target.getName() + " : " + token,
                    NamedTextColor.YELLOW));
            return true;
        }
        if (!(sender instanceof Player player) || !sender.hasPermission("tropicube.staff")) return false;
        if (args.length >= 2 && args[0].equalsIgnoreCase("enroll")) {
            try {
                StaffSecurityService.Enrollment enrollment = security.beginEnrollment(player.getUniqueId(), args[1]);
                String uri = "otpauth://totp/Tropicube:" + URLEncoder.encode(player.getName(), StandardCharsets.UTF_8)
                        + "?secret=" + enrollment.secret() + "&issuer=Tropicube";
                player.sendMessage(Component.text("Ajoute ce compte dans ton application TOTP :", NamedTextColor.AQUA));
                player.sendMessage(Component.text(uri, NamedTextColor.WHITE));
                player.sendMessage(Component.text("Codes de récupération (à conserver hors jeu) : "
                        + String.join(" ", enrollment.recoveryCodes()), NamedTextColor.GOLD));
                player.sendMessage(Component.text("Confirme avec /2fa confirm <code>.", NamedTextColor.GRAY));
            } catch (IllegalArgumentException error) {
                player.sendMessage(Component.text(error.getMessage(), NamedTextColor.RED));
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("confirm")) {
            security.confirm(player.getUniqueId(), args[1]).thenAccept(ok -> reply(player, ok));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("verify")) {
            security.verify(player.getUniqueId(), args[1]).thenAccept(ok -> reply(player, ok));
            return true;
        }
        player.sendMessage(Component.text(security.hasSession(player.getUniqueId())
                ? "Session staff vérifiée." : "Session non vérifiée. Utilise /2fa verify <code>.", NamedTextColor.AQUA));
        return true;
    }

    private void reply(Player player, boolean ok) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(Component.text(ok
                ? "Session staff validée pour 15 minutes." : "Code invalide ou déjà utilisé.",
                ok ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }
}
