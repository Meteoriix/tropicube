package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.network.StaffSecurityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Console-assisted TOTP enrollment and secondary staff-session validation. */
public final class TwoFactorCommand implements CommandExecutor, TabCompleter {
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
        if (args.length == 2 && args[0].equalsIgnoreCase("issue")) {
            if (!sender.hasPermission("tropicube.2fa.issue")) {
                if (sender instanceof Player player) message(player, "general.no-permission");
                return true;
            }
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                if (sender instanceof Player player) message(player, "two-factor.player-online-required");
                else sender.sendMessage(Component.text("The player must be online."));
                return true;
            }
            if (!target.hasPermission("tropicube.staff")) {
                if (sender instanceof Player player) message(player, "two-factor.staff-required");
                else sender.sendMessage(Component.text("The target does not have the staff permission."));
                return true;
            }
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    String token = security.issueEnrollment(target.getUniqueId());
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (sender instanceof Player player) message(player, "two-factor.issued", target.getName(), token);
                        else sender.sendMessage(Component.text("Enrollment token for " + target.getName() + ": " + token));
                    });
                } catch (RuntimeException error) {
                    syncFailure(sender);
                }
            });
            return true;
        }
        if (!(sender instanceof Player player)) return true;
        if (!sender.hasPermission("tropicube.staff")) {
            message(player, "general.no-permission");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("enroll")) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    StaffSecurityService.Enrollment enrollment = security.beginEnrollment(player.getUniqueId(), args[1]);
                    plugin.getServer().getScheduler().runTask(plugin, () -> showEnrollment(player, enrollment));
                } catch (IllegalArgumentException error) {
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> message(player, "two-factor.enrollment-failed"));
                } catch (RuntimeException error) {
                    syncFailure(player);
                }
            });
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("confirm")) {
            security.confirm(player.getUniqueId(), args[1].trim()).whenComplete((ok, error) -> reply(player, ok, error));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("verify")) {
            security.verify(player.getUniqueId(), args[1].trim()).whenComplete((ok, error) -> reply(player, ok, error));
            return true;
        }
        if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("status")) {
            if (security.hasSession(player.getUniqueId())) {
                message(player, "two-factor.session-active");
            } else {
                security.isEnrolled(player.getUniqueId()).whenComplete((enrolled, error) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> message(player,
                                error != null ? "general.operation-failed" : enrolled
                                        ? "two-factor.session-inactive" : "two-factor.not-enrolled")));
            }
            return true;
        }
        message(player, "two-factor.usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command,
                                      @NonNull String alias, String @NonNull [] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("issue")
                && sender.hasPermission("tropicube.2fa.issue")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return plugin.getServer().getOnlinePlayers().stream()
                    .filter(player -> player.hasPermission("tropicube.staff"))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> choices = new ArrayList<>();
        if (sender.hasPermission("tropicube.staff")) choices.addAll(List.of("status", "enroll", "confirm", "verify"));
        if (sender.hasPermission("tropicube.2fa.issue")) choices.add("issue");
        return choices.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private void showEnrollment(Player player, StaffSecurityService.Enrollment enrollment) {
        String encodedName = URLEncoder.encode(player.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        String uri = "otpauth://totp/Tropicube:" + encodedName
                + "?secret=" + enrollment.secret() + "&issuer=Tropicube";
        message(player, "two-factor.enroll-uri");
        player.sendMessage(Component.text(uri, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.copyToClipboard(uri))
                .hoverEvent(HoverEvent.showText(plugin.getLanguageManager().getComponent(
                        player.getUniqueId(), "two-factor.copy-hover"))));
        String recoveryCodes = String.join(" ", enrollment.recoveryCodes());
        message(player, "two-factor.recovery-codes", recoveryCodes);
        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "two-factor.copy-recovery")
                .clickEvent(ClickEvent.copyToClipboard(recoveryCodes)));
        message(player, "two-factor.confirm-help");
    }

    private void reply(Player player, Boolean ok, Throwable error) {
        plugin.getServer().getScheduler().runTask(plugin, () -> message(player,
                error != null ? "general.operation-failed" : Boolean.TRUE.equals(ok)
                        ? "two-factor.verified" : "two-factor.invalid-code"));
    }

    private void syncFailure(CommandSender sender) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (sender instanceof Player player) message(player, "general.operation-failed");
            else sender.sendMessage(Component.text("TOTP operation failed.", NamedTextColor.RED));
        });
    }

    private void message(Player player, String key, Object... arguments) {
        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), key, arguments));
    }
}
