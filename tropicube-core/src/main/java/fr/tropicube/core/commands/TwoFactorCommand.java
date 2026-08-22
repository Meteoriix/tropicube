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
            if (sender instanceof Player player) message(player, "two-factor.unavailable");
            else sender.sendMessage(Component.text("TOTP unavailable: master key is not configured."));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("issue")) {
            if (!sender.hasPermission("tropicube.2fa.issue")) return false;
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                if (sender instanceof Player player) message(player, "two-factor.player-online-required");
                else sender.sendMessage(Component.text("The player must be online."));
                return true;
            }
            String token = security.issueEnrollment(target.getUniqueId());
            if (sender instanceof Player player) message(player, "two-factor.issued", target.getName(), token);
            else sender.sendMessage(Component.text("Enrollment token for " + target.getName() + ": " + token));
            return true;
        }
        if (!(sender instanceof Player player) || !sender.hasPermission("tropicube.staff")) return false;
        if (args.length >= 2 && args[0].equalsIgnoreCase("enroll")) {
            try {
                StaffSecurityService.Enrollment enrollment = security.beginEnrollment(player.getUniqueId(), args[1]);
                String uri = "otpauth://totp/Tropicube:" + URLEncoder.encode(player.getName(), StandardCharsets.UTF_8)
                        + "?secret=" + enrollment.secret() + "&issuer=Tropicube";
                message(player, "two-factor.enroll-uri");
                player.sendMessage(Component.text(uri, NamedTextColor.WHITE));
                message(player, "two-factor.recovery-codes", String.join(" ", enrollment.recoveryCodes()));
                message(player, "two-factor.confirm-help");
            } catch (IllegalArgumentException error) {
                message(player, "two-factor.enrollment-failed");
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
        message(player, security.hasSession(player.getUniqueId())
                ? "two-factor.session-active" : "two-factor.session-inactive");
        return true;
    }

    private void reply(Player player, boolean ok) {
        plugin.getServer().getScheduler().runTask(plugin, () -> message(player,
                ok ? "two-factor.verified" : "two-factor.invalid-code"));
    }

    private void message(Player player, String key, Object... arguments) {
        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), key, arguments));
    }
}
