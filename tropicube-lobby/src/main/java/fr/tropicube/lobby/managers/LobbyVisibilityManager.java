package fr.tropicube.lobby.managers;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.network.PlayerPreferenceService;
import fr.tropicube.docker.model.PartySnapshot;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Applies each viewer's persistent all/friends/party/nobody entity filter. */
public final class LobbyVisibilityManager {
    private final TropicubeLobby plugin;
    private final TropicubeCore core;

    public LobbyVisibilityManager(TropicubeLobby plugin, TropicubeCore core) {
        this.plugin = plugin;
        this.core = core;
    }

    public void refreshAll() { Bukkit.getOnlinePlayers().forEach(this::refresh); }

    public void refresh(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        core.getPlayerPreferenceService().load(viewerId).thenCompose(preferences ->
                allowed(viewerId, preferences.lobbyVisibility()).thenApply(allowed ->
                        new Snapshot(preferences, allowed))).thenAccept(snapshot ->
                Bukkit.getScheduler().runTask(plugin, () -> apply(viewerId, snapshot)));
    }

    private CompletableFuture<Set<UUID>> allowed(UUID viewerId,
                                                   PlayerPreferenceService.LobbyVisibility visibility) {
        return switch (visibility) {
            case EVERYONE, NOBODY -> CompletableFuture.completedFuture(Set.of());
            case FRIENDS -> core.getSocialService().friends(viewerId).thenApply(friends -> {
                Set<UUID> ids = new HashSet<>();
                friends.forEach(friend -> ids.add(friend.playerId()));
                return Set.copyOf(ids);
            });
            case PARTY -> CompletableFuture.supplyAsync(() -> {
                PartySnapshot party = core.getSocialService().party(viewerId);
                if (party == null) return Set.<UUID>of();
                Set<UUID> ids = new HashSet<>();
                party.members().forEach(member -> ids.add(member.playerId()));
                return Set.copyOf(ids);
            });
        };
    }

    private void apply(UUID viewerId, Snapshot snapshot) {
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null) return;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) continue;
            boolean hiddenStaff = core.isStaffMode(target.getUniqueId()) && !viewer.hasPermission("tropicube.staff");
            boolean show = !hiddenStaff && switch (snapshot.preferences().lobbyVisibility()) {
                case EVERYONE -> true;
                case NOBODY -> false;
                case FRIENDS, PARTY -> snapshot.allowed().contains(target.getUniqueId());
            };
            if (show) viewer.showPlayer(plugin, target); else viewer.hidePlayer(plugin, target);
        }
        if (snapshot.preferences().contextualHelp()) core.getContextualHelpService().claim(viewerId, "LOBBY_JOIN")
                .thenAccept(show -> {
                    if (!show) return;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Player online = Bukkit.getPlayer(viewerId);
                        if (online != null) online.sendMessage(LangHelper.component(online, "lobby.contextual-help"));
                    }, 80L);
                });
    }

    public void forget(UUID playerId) { }

    private record Snapshot(PlayerPreferenceService.Preferences preferences, Set<UUID> allowed) {}
}
