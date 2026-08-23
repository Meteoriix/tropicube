package fr.tropicube.core.social;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.docker.model.PartySnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** Network social facade used by commands and the lobby UI. */
public final class SocialService {
    private static final String EVENT_PREFIX = "SOCIAL_MESSAGE:";
    private static final Pattern MINECRAFT_USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final TropicubeCore plugin;
    private final FriendshipRepository friendships;
    private final int maximumFriends;
    private final int maximumPartySize;
    private final int invitationSeconds;

    public SocialService(TropicubeCore plugin, FriendshipRepository friendships,
                         int maximumFriends, int maximumPartySize, int invitationSeconds) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.friendships = Objects.requireNonNull(friendships, "friendships");
        this.maximumFriends = maximumFriends;
        this.maximumPartySize = maximumPartySize;
        this.invitationSeconds = invitationSeconds;
        plugin.getRedisManager().subscribeToPlayerEvents(this::receiveNotification);
    }

    public CompletableFuture<FriendshipRepository.RequestResult> requestFriend(UUID requester, UUID target) {
        return plugin.getDatabaseManager().supplyAsync(() -> friendships.request(requester, target, maximumFriends));
    }

    public CompletableFuture<Boolean> acceptFriend(UUID target, UUID requester) {
        return plugin.getDatabaseManager().supplyAsync(() -> friendships.accept(target, requester));
    }

    public CompletableFuture<Boolean> denyFriend(UUID target, UUID requester) {
        return plugin.getDatabaseManager().supplyAsync(() -> friendships.deny(target, requester));
    }

    public CompletableFuture<Boolean> cancelFriend(UUID requester, UUID target) {
        return plugin.getDatabaseManager().supplyAsync(() -> friendships.cancel(requester, target));
    }

    public CompletableFuture<Boolean> removeFriend(UUID player, UUID friend) {
        return plugin.getDatabaseManager().supplyAsync(() -> friendships.remove(player, friend));
    }

    public CompletableFuture<Boolean> areFriends(UUID first, UUID second) {
        return plugin.getDatabaseManager().supplyAsync(() -> friendships.areFriends(first, second));
    }

    public CompletableFuture<List<FriendshipRepository.FriendView>> friends(UUID player) {
        return plugin.getDatabaseManager().supplyAsync(() -> friendships.friends(player));
    }

    public CompletableFuture<List<FriendshipRepository.PendingRequest>> requests(UUID player) {
        return plugin.getDatabaseManager().supplyAsync(() -> friendships.requests(player));
    }

    public CompletableFuture<List<FriendshipRepository.SentRequest>> sentRequests(UUID player) {
        return plugin.getDatabaseManager().supplyAsync(() -> friendships.sentRequests(player));
    }

    public CompletableFuture<Optional<UUID>> resolvePlayer(String username) {
        return plugin.getDatabaseManager().supplyAsync(() -> plugin.getPlayerDataManager().getUuidByName(username));
    }

    public boolean isOnline(UUID playerId) {
        return plugin.getRedisManager().exists("player:online:" + playerId);
    }

    public String onlineName(UUID playerId) {
        return plugin.getRedisManager().get("player:name:" + playerId);
    }

    public String serverInstance(UUID playerId) {
        return plugin.getRedisManager().getPlayerServer(playerId.toString());
    }

    public PartySnapshot party(UUID playerId) { return plugin.getRedisManager().getParty(playerId); }
    public Map<UUID, String> partyInvites(UUID playerId) { return plugin.getRedisManager().getPartyInvites(playerId); }
    public String inviteParty(UUID leader, UUID target) {
        return plugin.getRedisManager().inviteToParty(leader, target, maximumPartySize, invitationSeconds);
    }
    public String acceptParty(UUID target, UUID leader) {
        return plugin.getRedisManager().acceptPartyInvite(target, leader, maximumPartySize);
    }
    public void denyParty(UUID target, UUID leader) { plugin.getRedisManager().denyPartyInvite(target, leader); }
    public boolean setFollow(UUID player, boolean enabled) { return plugin.getRedisManager().setPartyFollow(player, enabled); }
    public String leaveParty(UUID player) { return plugin.getRedisManager().leaveParty(player); }
    public String kickParty(UUID leader, UUID target) { return plugin.getRedisManager().kickPartyMember(leader, target); }
    public boolean promoteParty(UUID leader, UUID target) { return plugin.getRedisManager().promotePartyMember(leader, target); }
    public boolean disbandParty(UUID leader) { return plugin.getRedisManager().disbandParty(leader); }

    public void requestFriendJoin(UUID requester, UUID target, String instanceId) {
        plugin.getRedisManager().publishCommand("PROXY", "FRIEND_JOIN:" + requester + ":" + target + ":" + instanceId);
    }

    public void requestPartyWarp(UUID leader) {
        plugin.getRedisManager().publishCommand("PROXY", "PARTY_WARP:" + leader);
    }

    public void requestPartyWarp(UUID leader, UUID target) {
        plugin.getRedisManager().publishCommand("PROXY", "PARTY_WARP_MEMBER:" + leader + ":" + target);
    }

    public void notifyPlayer(UUID target, String key, Object... arguments) {
        String rawArguments = String.join("\t", java.util.Arrays.stream(arguments).map(String::valueOf).toList());
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rawArguments.getBytes(StandardCharsets.UTF_8));
        plugin.getRedisManager().publishPlayerEvent("SOCIAL_MESSAGE", target + ":" + key + ":" + encoded);
    }

    public String displayName(UUID playerId) {
        String online = onlineName(playerId);
        return online == null || online.isBlank() ? playerId.toString().substring(0, 8) : online;
    }

    private void receiveNotification(String message) {
        if (!message.startsWith(EVENT_PREFIX)) return;
        String[] parts = message.substring(EVENT_PREFIX.length()).split(":", 3);
        if (parts.length != 3) return;
        try {
            UUID target = UUID.fromString(parts[0]);
            String decoded = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            Object[] arguments = decoded.isEmpty() ? new Object[0] : decoded.split("\\t", -1);
            Bukkit.getScheduler().runTask(plugin, () -> {
                var player = Bukkit.getPlayer(target);
                if (player != null) player.sendMessage(notificationComponent(target, parts[1], arguments));
            });
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed internal messages rather than exposing them to players.
        }
    }

    private Component notificationComponent(UUID target, String key, Object[] arguments) {
        Component message = plugin.getLanguageManager().getComponent(target, key, arguments);
        InvitationAction action = invitationAction(key, arguments);
        if (action == null) return message;
        Component hover = plugin.getLanguageManager().getComponent(target, action.hoverKey());
        return clickableInvitation(message, hover, action);
    }

    static Component clickableInvitation(Component message, Component hover, InvitationAction action) {
        return message.clickEvent(ClickEvent.runCommand(action.command()))
                .hoverEvent(HoverEvent.showText(hover));
    }

    /** Builds only allow-listed invitation commands from a valid Minecraft username. */
    static InvitationAction invitationAction(String key, Object[] arguments) {
        if (arguments.length == 0) return null;
        String username = String.valueOf(arguments[0]);
        if (!MINECRAFT_USERNAME.matcher(username).matches()) return null;
        return switch (key) {
            case "social.friend-request-received" -> new InvitationAction(
                    "/friend accept " + username, "social.friend-request-accept-hover");
            case "social.party-invite-received" -> new InvitationAction(
                    "/party accept " + username, "social.party-invite-accept-hover");
            default -> null;
        };
    }

    record InvitationAction(String command, String hoverKey) { }

    public void expireRequests(int expiryDays) {
        plugin.getDatabaseManager().supplyAsync(() -> { friendships.expireRequests(expiryDays); return null; });
    }
}
