package fr.tropicube.lobby.managers;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.ServerInstance;
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
                        instance.getTemplateId() != null ? instance.getTemplateId() : ""
                );
                fresh.put(info.id(), info);
            }

            // Atomic replacement: Concurrent reads always see a consistent snapshot.
            cacheRef.set(Collections.unmodifiableMap(fresh));
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur rafraîchissement serveurs : " + e.getMessage());
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
                    .map(t -> new TemplateInfo(t.id(), t.name(), t.type(), Math.max(1, t.maxPlayers())))
                    .toList();
            templateCacheRef.set(templates);
        } catch (JsonParseException | IllegalStateException e) {
            plugin.getLogger().warning("Erreur lecture templates : " + e.getMessage());
        }
    }

    /** Returns all servers (all categories). */
    public Collection<ServerInfo> getAllServers() {
        return List.copyOf(cacheRef.get().values());
    }

    /** Returns servers filtered by type (eg: "survival", "lobby", "pvp"), sorted by id. */
    public List<ServerInfo> getServersByType(String type) {
        List<ServerInfo> result = new ArrayList<>();
        for (ServerInfo info : cacheRef.get().values()) {
            if (info.type().equalsIgnoreCase(type)) result.add(info);
        }
        result.sort(Comparator.comparing(ServerInfo::id));
        return result;
    }

    /** Returns the distinct server types available (excluding lobby). */
    public Set<String> getAvailableTypes() {
        Set<String> types = new TreeSet<>();
        for (ServerInfo info : cacheRef.get().values()) {
            if (!"lobby".equalsIgnoreCase(info.type())) {
                types.add(info.type());
            }
        }
        return types;
    }

    /** Returns distinct types published by Velocity templates (excluding lobby). */
    public Set<String> getAvailableTemplateTypes() {
        Set<String> types = new TreeSet<>();
        for (TemplateInfo t : getCustomGameTemplates()) {
            if (!"LOBBY".equalsIgnoreCase(t.type())) {
                types.add(t.type());
            }
        }
        return types;
    }

    public Optional<ServerInfo> getServer(String id) {
        return Optional.ofNullable(cacheRef.get().get(id));
    }

    /** Finds the best available server of a given type (fewer players, ONLINE, not full). */
    public Optional<ServerInfo> getBestServer(String type) {
        return getServersByType(type).stream()
                .filter(ServerInfo::isMatchmakingJoinable)
                .min(Comparator.comparingInt(ServerInfo::playerCount));
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

    /**
     * Reads the list of templates published by Velocity from Redis.
     * Returns an empty list if no template is available.
     */
    public List<TemplateInfo> getCustomGameTemplates() {
        return templateCacheRef.get();
    }

    /**
     * Immutable data from a server template (published by Velocity).
     */
    public record TemplateInfo(String id, String name, String type, int maxPlayers) {}

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
            String templateName
    ) {
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
