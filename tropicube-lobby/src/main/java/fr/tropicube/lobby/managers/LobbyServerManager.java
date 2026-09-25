package fr.tropicube.lobby.managers;

import fr.tropicube.core.util.MessageStyle;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.docker.model.InstanceMode;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads and caches information from servers available from Redis.
 * Used by GUI menus and lobby commands.
 */
public class LobbyServerManager {

    private final TropicubeLobby plugin;
    private final RedisManager redisManager;
    private final Gson gson = new Gson();

    // Atomic reference: the replacement of the entire map is atomic (no partial reading).
    private final AtomicReference<Map<String, ServerInfo>> cacheRef =
            new AtomicReference<>(Collections.emptyMap());
    private final AtomicReference<List<TemplateInfo>> templateCacheRef =
            new AtomicReference<>(List.of());
    private final AtomicReference<Map<String, RankedQueueStats>> rankedStatsCacheRef =
            new AtomicReference<>(Map.of());
    private final Map<UUID, String> activeMatchmakingCache = new java.util.concurrent.ConcurrentHashMap<>();

    public LobbyServerManager(TropicubeLobby plugin, RedisManager redisManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
    }

    /** Refreshes the list of servers from Redis (called asynchronously). */
    public void refreshServerList() {
        try {
            List<ServerInstance> instances = redisManager.getAllInstances();
            Map<String, ServerInfo> fresh = new HashMap<>(instances.size() * 2);

            for (ServerInstance instance : instances) {
                if (instance.getServerName() == null || instance.getServerName().isBlank()) continue;
                ServerInfo info = new ServerInfo(
                        instance.getServerName(),
                        instance.getServerType() != null ? instance.getServerType() : "unknown",
                        instance.getHost() != null ? instance.getHost() : "localhost",
                        instance.getPort(),
                        instance.getOnlinePlayers(),
                        instance.getMaxPlayers(),
                        instance.getStatus() != null ? instance.getStatus().name() : "UNKNOWN",
                        instance.getTemplateId() != null ? instance.getTemplateId() : "",
                        instance.getMode() == null ? InstanceMode.QUICK_PLAY : instance.getMode(),
                        instance.isWhitelisted(),
                        instance.getWhitelistedPlayers()
                );
                fresh.put(info.id(), info);
            }

            // Atomic replacement: Concurrent reads always see a consistent snapshot.
            cacheRef.set(Collections.unmodifiableMap(fresh));
        } catch (Exception e) {
            plugin.getLogger().warning(MessageStyle.log("tc", "LOBBY_SERVER", "<yellow>Erreur rafraîchissement serveurs : " + e.getMessage()));
        }
        refreshTemplateList();
    }

    private void refreshTemplateList() {
        try {
            String json = redisManager.getTemplatesJson();
            if (json == null || json.isBlank()) {
                templateCacheRef.set(List.of());
                return;
            }
            TemplateInfo[] parsed = gson.fromJson(json, TemplateInfo[].class);
            if (parsed == null) {
                templateCacheRef.set(List.of());
                return;
            }
            List<TemplateInfo> templates = Arrays.stream(parsed)
                    .filter(Objects::nonNull)
                    .filter(t -> t.id() != null && !t.id().isBlank())
                    .filter(t -> t.name() != null && !t.name().isBlank())
                    .filter(t -> t.type() != null && !t.type().isBlank())
                    .map(t -> new TemplateInfo(t.id(), t.name(), t.type(), Math.max(1, t.maxPlayers()),
                            t.mode() == null ? InstanceMode.QUICK_PLAY : t.mode(),
                            t.gameType(), t.gameFormat()))
                    .toList();
            templateCacheRef.set(templates);
            refreshRankedStats(templates);
        } catch (JsonParseException | IllegalStateException e) {
            plugin.getLogger().warning(MessageStyle.log("tc", "LOBBY_SERVER", "<yellow>Erreur lecture templates : " + e.getMessage()));
        }
    }

    /** Returns all servers (all categories). */
    public Collection<ServerInfo> getAllServers() {
        return List.copyOf(cacheRef.get().values());
    }

    /** Returns servers filtered by type (eg: "survival", "lobby", "pvp"), sorted by id. */
    public List<ServerInfo> getServersByType(String type, UUID playerId) {
        List<ServerInfo> result = new ArrayList<>();
        for (ServerInfo info : cacheRef.get().values()) {
            if (info.type().equalsIgnoreCase(type) && info.isVisibleTo(playerId)
                    && !info.mode().isRanked()) result.add(info);
        }
        result.sort(Comparator.comparing(ServerInfo::id));
        return result;
    }

    /** Returns distinct types published by Velocity templates (excluding lobby). */
    public Set<String> getAvailableTemplateTypes() {
        Set<String> types = new TreeSet<>();
        for (TemplateInfo t : templateCacheRef.get()) {
            if (!"LOBBY".equalsIgnoreCase(t.type()) && !t.mode().isRanked()) {
                types.add(t.type());
            }
        }
        return types;
    }

    public Optional<ServerInfo> getServer(String id) {
        return Optional.ofNullable(cacheRef.get().get(id));
    }

    /** Finds the best available server of a given type (fewer players, ONLINE, not full). */
    public Optional<ServerInfo> getBestServer(String type, UUID playerId) {
        return getBestServer(type, playerId, 1);
    }

    /** Fill-first selection that rejects instances unable to receive the complete party. */
    public Optional<ServerInfo> getBestServer(String type, UUID playerId, int groupSize) {
        return SmartServerSelector.select(getServersByType(type, playerId), groupSize);
    }

    public Optional<ServerInfo> getBestQuickPlayServer(String type, UUID playerId) {
        return SmartServerSelector.select(getServersByType(type, playerId).stream()
                .filter(server -> server.mode() == InstanceMode.QUICK_PLAY).toList(), 1);
    }

    public int getTotalPlayers() {
        return cacheRef.get().values().stream().mapToInt(ServerInfo::playerCount).sum();
    }

    /** Asks the Velocity proxy to transfer the player via Redis. */
    public void connectToServer(org.bukkit.entity.Player player, String serverName) {
        redisManager.publishCommand("PROXY", "CONNECT:" + player.getUniqueId() + ":" + serverName);
    }

    /** Returns the templateId for a given server type. */
    public Optional<String> getTemplateIdForType(String type) {
        return getCustomGameTemplates().stream()
                .filter(t -> type.equalsIgnoreCase(t.type()))
                .map(TemplateInfo::id)
                .findFirst();
    }

    public List<TemplateInfo> getRankedTemplatesForType(String type) {
        return templateCacheRef.get().stream()
                .filter(template -> type.equalsIgnoreCase(template.type()))
                .filter(template -> template.mode().isRanked())
                .sorted(Comparator.comparing(TemplateInfo::mode))
                .toList();
    }

    /** Returns the published metadata for one exact template identifier. */
    public Optional<TemplateInfo> getTemplate(String templateId) {
        return templateCacheRef.get().stream().filter(template -> template.id().equals(templateId)).findFirst();
    }

    /** Returns the enabled queue templates published for one player-facing category. */
    public List<TemplateInfo> getTemplatesForType(String type) {
        return templateCacheRef.get().stream()
                .filter(template -> type.equalsIgnoreCase(template.type()))
                .filter(template -> !template.mode().isRanked())
                .sorted(Comparator.comparing(TemplateInfo::id))
                .toList();
    }

    public Optional<RankedQueueStats> getRankedStats(String templateId) {
        RankedQueueStats stats = rankedStatsCacheRef.get().get(templateId);
        return stats == null || stats.updatedAt() < System.currentTimeMillis() - 15_000L
                ? Optional.empty() : Optional.of(stats);
    }

    public Optional<String> getActiveMatchmaking(UUID playerId) {
        return Optional.ofNullable(activeMatchmakingCache.get(playerId));
    }

    /** Resolves an internal template identifier to its player-facing configured name. */
    public String getTemplateDisplayName(String templateId) {
        return templateCacheRef.get().stream()
                .filter(template -> template.id().equals(templateId))
                .map(TemplateInfo::name)
                .findFirst()
                .orElse("—");
    }

    /** Refreshes one player's queue cache. Must be called away from the Paper thread. */
    public void refreshPlayerMatchmaking(UUID playerId) {
        try {
            String template = redisManager.get("matchmaking:player:" + playerId);
            if (template == null) activeMatchmakingCache.remove(playerId);
            else activeMatchmakingCache.put(playerId, template);
        } catch (RuntimeException error) {
            activeMatchmakingCache.remove(playerId);
        }
    }

    public long getMatchmakingSince(UUID playerId) {
        try {
            String raw = redisManager.get("sw:queue-since:" + playerId);
            return raw == null ? 0L : Long.parseLong(raw);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    public void cancelMatchmaking(org.bukkit.entity.Player player) {
        activeMatchmakingCache.remove(player.getUniqueId());
        redisManager.publishCommand("PROXY", "CANCEL_MATCHMAKING:" + player.getUniqueId());
    }

    private void refreshRankedStats(List<TemplateInfo> templates) {
        Map<String, RankedQueueStats> fresh = new HashMap<>();
        for (TemplateInfo template : templates) {
            if (!template.mode().isRanked()) continue;
            try {
                String raw = redisManager.get("matchmaking:ranked:stats:" + template.id());
                RankedQueueStats stats = raw == null ? null : gson.fromJson(raw, RankedQueueStats.class);
                if (stats != null && stats.version() == 1) fresh.put(template.id(), stats);
            } catch (RuntimeException ignored) { }
        }
        rankedStatsCacheRef.set(Map.copyOf(fresh));
    }

    /**
     * Asks the proxy to create and start a new server of the given type,
     * then redirect the player there as soon as he is ready.
     */
    public void requestStartGame(org.bukkit.entity.Player player, String type) {
        getTemplateIdForType(type).ifPresentOrElse(
                templateId -> redisManager.publishCommand("PROXY", "START_GAME:" + templateId + ":" + player.getUniqueId()),
                () -> player.sendMessage(LangHelper.component(player, "lobby.no-template-for-type", type))
        );
    }

    /** Requests one exact queue template, avoiding ambiguity between SheepWars modes. */
    public void requestStartTemplate(org.bukkit.entity.Player player, String templateId) {
        boolean available = templateCacheRef.get().stream().anyMatch(template -> template.id().equals(templateId));
        if (!available) {
            player.sendMessage(LangHelper.component(player, "lobby.no-template-for-type", templateId));
            return;
        }
        redisManager.publishCommand("PROXY", "START_GAME:" + templateId + ":" + player.getUniqueId());
    }

    /**
     * Reads the list of templates published by Velocity from Redis.
     * Returns an empty list if no template is available.
     */
    public List<TemplateInfo> getCustomGameTemplates() {
        return templateCacheRef.get().stream()
                .filter(template -> !template.mode().isRanked())
                .filter(template -> !"BETA".equalsIgnoreCase(template.type()))
                .toList();
    }

    /**
     * Immutable data from a server template (published by Velocity).
     */
    public record TemplateInfo(String id, String name, String type, int maxPlayers, InstanceMode mode,
                               String gameType, String gameFormat) {
        /** Compatibility constructor for callers and cached JSON created before queue display metadata. */
        public TemplateInfo(String id, String name, String type, int maxPlayers, InstanceMode mode) {
            this(id, name, type, maxPlayers, mode, type, null);
        }
    }
    public record RankedQueueStats(int version, String templateId, int groups, int reservedPlayers,
                                   int capacity, long oldestWaitSeconds, long updatedAt) {}

    /**
     * Immutable data from a server (Redis snapshot).
     */
    public record ServerInfo(
            String id,
            String type,
            String host,
            int port,
            int playerCount,
            int maxPlayers,
            String status,
            String templateName,
            InstanceMode mode,
            boolean privateGame,
            List<UUID> whitelistedPlayers
    ) {
        public ServerInfo {
            whitelistedPlayers = List.copyOf(Objects.requireNonNullElse(whitelistedPlayers, List.of()));
        }

        /** Private instances are invisible unless this UUID was explicitly admitted. */
        public boolean isVisibleTo(UUID playerId) {
            return !privateGame || (playerId != null && whitelistedPlayers.contains(playerId));
        }
        public boolean isOnline()   {
            return "GAME_WAITING".equalsIgnoreCase(status)
                    || "GAME_STARTING".equalsIgnoreCase(status)
                    || "GAME_PLAYING".equalsIgnoreCase(status)
                    || "GAME_ENDING".equalsIgnoreCase(status);
        }
        public boolean isJoinable() {
            return ("GAME_WAITING".equalsIgnoreCase(status)
                    || "GAME_STARTING".equalsIgnoreCase(status)
                    || "GAME_PLAYING".equalsIgnoreCase(status)) && !isFull();
        }
        public boolean isMatchmakingJoinable() {
            return ("GAME_WAITING".equalsIgnoreCase(status)
                    || "GAME_STARTING".equalsIgnoreCase(status)) && !isFull();
        }
        /** Indicates whether the instance should appear in the lobby lists and totals. */
        public boolean isListed() { return isOnline() || isStarting(); }
        public boolean isStarting() { return "STARTING".equalsIgnoreCase(status); }
        public boolean isPlaying() {
            return "GAME_PLAYING".equalsIgnoreCase(status) || "PLAYING".equalsIgnoreCase(status);
        }
        public boolean isFull()     { return playerCount >= maxPlayers; }
        public String displayName() {
            return id == null || id.isBlank()
                    ? "Serveur"
                    : id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
        }
    }
}
