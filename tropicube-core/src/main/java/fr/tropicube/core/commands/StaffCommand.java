package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Verified inter-server staff chat and controlled invisible spectator mode. */
public final class StaffCommand implements CommandExecutor {
    private static final String EVENT = "STAFF_CHAT:";
    private final TropicubeCore plugin;

    public StaffCommand(TropicubeCore plugin) {
        this.plugin = plugin;
        plugin.getRedisManager().subscribeToPlayerEvents(this::receive);
    }

    @Override public boolean onCommand(CommandSender sender, @NonNull Command command,
                                       @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("tropicube.staff")) return false;
        if (!plugin.getStaffSecurityService().hasSession(player.getUniqueId())) {
            player.sendMessage(Component.text("Valide d'abord ta session avec /2fa verify <code>.", NamedTextColor.RED));
            return true;
        }
        if (label.equalsIgnoreCase("staffchat") || label.equalsIgnoreCase("sc")) {
            if (args.length == 0) return false;
            String body = String.join(" ", args);
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (player.getName() + "\n" + body).getBytes(StandardCharsets.UTF_8));
            plugin.getRedisManager().publishPlayerEvent("STAFF_CHAT", payload);
            return true;
        }
        String key = "staff-mode:" + player.getUniqueId();
        boolean enable = !plugin.isStaffMode(player.getUniqueId());
        if (enable) {
            plugin.setStaffMode(player.getUniqueId(), true);
            plugin.getRedisManager().set(key, "active", 8 * 60 * 60);
            player.setGameMode(GameMode.SPECTATOR);
            Bukkit.getOnlinePlayers().stream().filter(viewer -> !viewer.hasPermission("tropicube.staff"))
                    .forEach(viewer -> viewer.hidePlayer(plugin, player));
        } else {
            plugin.setStaffMode(player.getUniqueId(), false);
            plugin.getRedisManager().delete(key);
            player.setGameMode(GameMode.ADVENTURE);
            Bukkit.getOnlinePlayers().forEach(viewer -> viewer.showPlayer(plugin, player));
        }
        player.sendMessage(Component.text("Mode staff " + (enable ? "activé" : "désactivé") + ".",
                enable ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        return true;
    }

    private void receive(String raw) {
        if (!raw.startsWith(EVENT)) return;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(raw.substring(EVENT.length())), StandardCharsets.UTF_8);
            String[] fields = decoded.split("\\n", 2);
            if (fields.length != 2) return;
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().stream()
                    .filter(player -> player.hasPermission("tropicube.staff")
                            && plugin.getStaffSecurityService().hasSession(player.getUniqueId()))
                    .forEach(player -> player.sendMessage(Component.text("[Staff] ", NamedTextColor.GOLD)
                            .append(Component.text(fields[0] + " > ", NamedTextColor.WHITE))
                            .append(Component.text(fields[1], NamedTextColor.YELLOW)))));
        } catch (RuntimeException ignored) {
            // Untrusted Redis payload: ignore malformed data.
        }
    }
}
