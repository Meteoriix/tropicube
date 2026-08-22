package fr.tropicube.lobby.commands;

import fr.tropicube.docker.model.PartySnapshot;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/** Routes a party leader to Quick Play or either shared-rating ranked queue. */
public final class SheepWarsQueueCommand implements CommandExecutor {
    private final TropicubeLobby plugin;

    public SheepWarsQueueCommand(TropicubeLobby plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        Queue queue = resolve(command.getName(), args);
        if (queue == null) {
            player.sendMessage(LangHelper.component(player, "lobby.sheepwars-queue-usage"));
            return true;
        }
        CompletableFuture.supplyAsync(() -> inspect(player, queue)).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> apply(player, queue, result)));
        return true;
    }

    private Inspection inspect(Player player, Queue queue) {
        PartySnapshot party = plugin.getRedisManager().getParty(player.getUniqueId());
        if (party != null && !party.isLeader(player.getUniqueId())) return new Inspection(false, "leader", 0);
        int size = party == null ? 1 : party.members().size();
        if (size > queue.maximumPartySize) return new Inspection(false, "size", size);
        if (queue.ranked) {
            var transferredMembers = party == null ? java.util.List.of(player.getUniqueId()) : party.members().stream()
                    .filter(member -> member.playerId().equals(party.leaderId()) || member.followEnabled())
                    .map(fr.tropicube.docker.model.PartyMember::playerId).toList();
            for (var memberId : transferredMembers) {
                String rawPenalty = plugin.getRedisManager().get("sw:ranked-penalty:" + memberId);
                if (rawPenalty == null) continue;
                try {
                    long seconds = (Long.parseLong(rawPenalty) - System.currentTimeMillis() + 999) / 1000;
                    if (seconds > 0) return new Inspection(false, "penalty", seconds);
                } catch (NumberFormatException ignored) { }
            }
            var profile = plugin.getCore().getProfileService().view(player.getUniqueId(), player.getUniqueId()).join();
            double rating = profile == null ? 1500 : profile.rating();
            plugin.getRedisManager().set("sw:queue-rating:" + player.getUniqueId(), Double.toString(rating), 1800);
            plugin.getRedisManager().set("sw:queue-size:" + player.getUniqueId(),
                    Integer.toString(transferredMembers.size()), 1800);
            plugin.getRedisManager().set("sw:queue-since:" + player.getUniqueId(),
                    Long.toString(System.currentTimeMillis()), 1800);
        }
        return new Inspection(true, "", size);
    }

    private void apply(Player player, Queue queue, Inspection result) {
        if (!player.isOnline()) return;
        if (!result.allowed) {
            String key = switch (result.reason) {
                case "leader" -> "lobby.sheepwars-queue-leader-only";
                case "size" -> "lobby.sheepwars-queue-party-too-large";
                default -> "lobby.sheepwars-queue-penalty";
            };
            player.sendMessage(LangHelper.component(player, key, result.value));
            return;
        }
        plugin.getLobbyServerManager().requestStartTemplate(player, queue.templateId);
        player.sendMessage(LangHelper.component(player, "lobby.sheepwars-queue-joined", queue.label));
    }

    private Queue resolve(String command, String[] args) {
        if (command.equalsIgnoreCase("quickplay")) return new Queue("sheepwars", "Quick Play", 4, false);
        if (args.length != 1) return null;
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "4v4" -> new Queue("sheepwars-ranked-4v4", "4v4", 2, true);
            case "8v8" -> new Queue("sheepwars-ranked-8v8", "8v8", 4, true);
            default -> null;
        };
    }

    private record Queue(String templateId, String label, int maximumPartySize, boolean ranked) {}
    private record Inspection(boolean allowed, String reason, long value) {}
}
