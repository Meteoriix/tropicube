package fr.tropicube.lobby.managers;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Fill-first lobby policy that preserves enough room for the whole party. */
public final class SmartServerSelector {
    private SmartServerSelector() {}

    public static Optional<LobbyServerManager.ServerInfo> select(
            List<LobbyServerManager.ServerInfo> servers, int groupSize) {
        int required = Math.max(1, groupSize);
        return servers.stream()
                .filter(LobbyServerManager.ServerInfo::isMatchmakingJoinable)
                .filter(server -> server.maxPlayers() - server.playerCount() >= required)
                .min(Comparator.comparingInt(SmartServerSelector::statePriority)
                        .thenComparing(Comparator.comparingInt(
                                LobbyServerManager.ServerInfo::playerCount).reversed())
                        .thenComparing(LobbyServerManager.ServerInfo::id));
    }

    private static int statePriority(LobbyServerManager.ServerInfo server) {
        return "GAME_STARTING".equalsIgnoreCase(server.status()) ? 0 : 1;
    }
}
