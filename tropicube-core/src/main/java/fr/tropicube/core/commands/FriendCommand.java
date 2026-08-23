package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.social.FriendshipRepository;
import fr.tropicube.core.social.SocialService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/** Player-facing persistent friendship command. */
public final class FriendCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "add", "accept", "deny", "cancel", "remove", "list", "requests", "join", "help");
    private final TropicubeCore plugin;
    private final SocialService social;

    public FriendCommand(TropicubeCore plugin) {
        this.plugin = plugin;
        this.social = plugin.getSocialService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getComponentForLang("fr", "general.player-only"));
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "social.friend-help"));
            return true;
        }
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        String[] arguments = args.clone();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> execute(playerId, playerName, arguments));
        return true;
    }

    private void execute(UUID playerId, String playerName, String[] args) {
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(subcommand)) { listFriends(playerId); return; }
        if ("requests".equals(subcommand)) { listRequests(playerId); return; }
        if (args.length != 2) {
            message(playerId, "social.friend-help");
            return;
        }
        resolve(args[1], playerId, target -> {
            if (target.equals(playerId)) {
                message(playerId, "social.self-target");
                return;
            }
            switch (subcommand) {
                case "add" -> add(playerId, playerName, target, args[1]);
                case "accept" -> accept(playerId, playerName, target, args[1]);
                case "deny" -> deny(playerId, target, args[1]);
                case "cancel" -> cancel(playerId, target, args[1]);
                case "remove" -> remove(playerId, target, args[1]);
                case "join" -> join(playerId, target, args[1]);
                default -> message(playerId, "social.friend-help");
            }
        });
    }

    private void listFriends(UUID playerId) {
        try {
            var friends = social.friends(playerId).join();
            message(playerId, "social.friend-list-header", friends.size());
            friends.forEach(friend -> message(playerId,
                    social.isOnline(friend.playerId()) ? "social.friend-list-online" : "social.friend-list-offline", friend.username()));
        } catch (RuntimeException exception) { failure(playerId); }
    }

    private void listRequests(UUID playerId) {
        try {
            var requests = social.requests(playerId).join();
            message(playerId, "social.friend-requests-header", requests.size());
            requests.forEach(request -> message(playerId, "social.friend-request-entry", request.username()));
        } catch (RuntimeException exception) { failure(playerId); }
    }

    private void add(UUID playerId, String playerName, UUID target, String targetName) {
        try {
            var result = social.requestFriend(playerId, target).join();
            String key = switch (result) {
                case CREATED -> "social.friend-request-sent";
                case ALREADY_FRIENDS -> "social.friend-already";
                case ALREADY_PENDING -> "social.friend-request-pending";
                case LIMIT_REACHED -> "social.friend-limit";
            };
            message(playerId, key, targetName);
            if (result == FriendshipRepository.RequestResult.CREATED) {
                social.notifyPlayer(target, "social.friend-request-received", playerName);
            }
        } catch (RuntimeException exception) { failure(playerId); }
    }

    private void accept(UUID playerId, String playerName, UUID requester, String requesterName) {
        try {
            boolean accepted = social.acceptFriend(playerId, requester).join();
            message(playerId, accepted ? "social.friend-accepted" : "social.friend-request-missing", requesterName);
            if (accepted) social.notifyPlayer(requester, "social.friend-accepted-by", playerName);
        } catch (RuntimeException exception) { failure(playerId); }
    }

    private void deny(UUID playerId, UUID requester, String requesterName) {
        try { message(playerId, social.denyFriend(playerId, requester).join()
                ? "social.friend-denied" : "social.friend-request-missing", requesterName); }
        catch (RuntimeException exception) { failure(playerId); }
    }

    private void cancel(UUID playerId, UUID target, String targetName) {
        try { message(playerId, social.cancelFriend(playerId, target).join()
                ? "social.friend-cancelled" : "social.friend-cancel-missing", targetName); }
        catch (RuntimeException exception) { failure(playerId); }
    }

    private void remove(UUID playerId, UUID friend, String friendName) {
        try { message(playerId, social.removeFriend(playerId, friend).join()
                ? "social.friend-removed" : "social.friend-not-found", friendName); }
        catch (RuntimeException exception) { failure(playerId); }
    }

    private void join(UUID playerId, UUID friend, String friendName) {
        try {
            boolean accepted = social.areFriends(playerId, friend).join();
            if (!accepted) {
                message(playerId, "social.friend-not-found", friendName);
                return;
            }
            String instanceId = social.serverInstance(friend);
            if (!social.isOnline(friend) || instanceId == null) {
                message(playerId, "social.friend-offline", friendName);
                return;
            }
            social.requestFriendJoin(playerId, friend, instanceId);
            message(playerId, "social.friend-join-requested", friendName);
        } catch (RuntimeException exception) { failure(playerId); }
    }

    private void resolve(String username, UUID playerId, Consumer<UUID> action) {
        try { social.resolvePlayer(username).join().ifPresentOrElse(action,
                () -> message(playerId, "social.player-unknown", username)); }
        catch (RuntimeException exception) { failure(playerId); }
    }

    private void message(UUID playerId, String key, Object... arguments) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player online = plugin.getServer().getPlayer(playerId);
            if (online != null) online.sendMessage(plugin.getLanguageManager().getComponent(playerId, key, arguments));
        });
    }

    private void failure(UUID playerId) { message(playerId, "general.operation-failed"); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return SUBCOMMANDS.stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
