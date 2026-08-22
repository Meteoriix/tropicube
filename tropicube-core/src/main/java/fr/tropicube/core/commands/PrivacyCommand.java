package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/** TOTP-protected staff entry point for GDPR data exports and delayed erasure. */
public final class PrivacyCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public PrivacyCommand(TropicubeCore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, @NonNull Command command,
                                       @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player staff) || !sender.hasPermission("tropicube.privacy.manage")) return false;
        if (!StaffSecurityGate.allow(plugin, sender)) return true;
        if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) {
            try {
                long id = Long.parseLong(args[1]);
                plugin.getPrivacyService().cancel(id).thenAccept(ok -> reply(staff,
                        ok ? "privacy.cancelled" : "privacy.not-found", id));
            } catch (NumberFormatException error) { reply(staff, "privacy.usage"); }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
            String language = plugin.getLanguageManager().getPlayerLanguage(staff.getUniqueId());
            CommandAsync.run(plugin, staff, language,
                    () -> plugin.getPlayerDataManager().getUuidByName(args[1]).orElse(null), target -> {
                        if (target == null) { reply(staff, "general.player-not-found", args[1]); return; }
                        plugin.getPrivacyService().requests(target).thenAccept(requests -> {
                            reply(staff, "privacy.status-header", args[1], requests.size());
                            requests.forEach(request -> reply(staff, "privacy.status-entry", request.id(),
                                    request.type(), request.status(), request.executeAfter()));
                        });
                    });
            return true;
        }
        if (args.length != 2 || !(args[0].equalsIgnoreCase("export") || args[0].equalsIgnoreCase("erase"))) {
            reply(staff, "privacy.usage"); return true;
        }
        String language = plugin.getLanguageManager().getPlayerLanguage(staff.getUniqueId());
        CommandAsync.run(plugin, staff, language,
                () -> plugin.getPlayerDataManager().getUuidByName(args[1]).orElse(null), target -> {
                    if (target == null) { reply(staff, "general.player-not-found", args[1]); return; }
                    if (args[0].equalsIgnoreCase("export")) plugin.getPrivacyService().export(target, staff.getUniqueId())
                            .thenAccept(id -> reply(staff, "privacy.export-created", id));
                    else plugin.getPrivacyService().requestAnonymization(target, staff.getUniqueId())
                            .thenAccept(id -> reply(staff, "privacy.erasure-created", id));
                });
        return true;
    }

    private void reply(Player player, String key, Object... arguments) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(
                plugin.getLanguageManager().getComponent(player.getUniqueId(), key, arguments)));
    }
}
