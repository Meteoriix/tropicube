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
 * Lit et met en cache les informations des serveurs disponibles depuis Redis.
 * Utilisé par les menus GUI et les commandes du lobby.
 */
public class LobbyServerManager {

    private final TropicubeLobby plugin;
    private final RedisManager redisManager;
    private final Gson gson = new Gson();

    // Référence atomique : le remplacement de toute la map est atomique (pas de lecture partielle).
    private final AtomicReference<Map<String, ServerInfo>> cacheRef =
            new AtomicReference<>(Collections.emptyMap());
    private final AtomicReference<List<TemplateInfo>> templateCacheRef =
            new AtomicReference<>(List.of());

    public LobbyServerManager(TropicubeLobby plugin, RedisManager redisManager) {
        this.plugin = plugin;
        this.redisManager = redisManager;
    }

    /** Rafraîchit la liste des serveurs depuis Redis (appelé de façon asynchrone). */
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

            // Remplacement atomique : les lectures concurrentes voient toujours un snapshot cohérent.
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

    /** Retourne tous les serveurs (toutes catégories). */
    public Collection<ServerInfo> getAllServers() {
        return List.copyOf(cacheRef.get().values());
    }

    /** Retourne les serveurs filtrés par type (ex: "survival", "lobby", "pvp"), triés par id. */
    public List<ServerInfo> getServersByType(String type) {
        List<ServerInfo> result = new ArrayList<>();
        for (ServerInfo info : cacheRef.get().values()) {
            if (info.type().equalsIgnoreCase(type)) result.add(info);
        }
        result.sort(Comparator.comparing(ServerInfo::id));
        return result;
    }

    /** Retourne les types de serveurs distincts disponibles (hors lobby). */
    public Set<String> getAvailableTypes() {
        Set<String> types = new TreeSet<>();
        for (ServerInfo info : cacheRef.get().values()) {
            if (!"lobby".equalsIgnoreCase(info.type())) {
                types.add(info.type());
            }
        }
        return types;
    }

    /** Retourne les types distincts publiés par les templates Velocity (hors lobby). */
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

    /** Trouve le meilleur serveur disponible d'un type donné (moins de joueurs, ONLINE, non plein). */
    public Optional<ServerInfo> getBestServer(String type) {
        return getServersByType(type).stream()
                .filter(ServerInfo::isMatchmakingJoinable)
                .min(Comparator.comparingInt(ServerInfo::playerCount));
    }

    public int getTotalPlayers() {
        return cacheRef.get().values().stream().mapToInt(ServerInfo::playerCount).sum();
    }

    /** Demande au proxy Velocity de transférer le joueur via Redis. */
    public void connectToServer(org.bukkit.entity.Player player, String serverName) {
        redisManager.publishCommand("PROXY", "CONNECT:" + player.getUniqueId() + ":" + serverName);
    }

    /** Retourne le templateId pour un type de serveur donné. */
    public Optional<String> getTemplateIdForType(String type) {
        return getCustomGameTemplates().stream()
                .filter(t -> type.equalsIgnoreCase(t.type()))
                .map(TemplateInfo::id)
                .findFirst();
    }

    /**
     * Demande au proxy de créer et démarrer un nouveau serveur du type donné,
     * puis d'y rediriger le joueur dès qu'il est prêt.
     */
    public void requestStartGame(org.bukkit.entity.Player player, String type) {
        getTemplateIdForType(type).ifPresentOrElse(
                templateId -> redisManager.publishCommand("PROXY", "START_GAME:" + templateId + ":" + player.getUniqueId()),
                () -> player.sendMessage(LangHelper.component(player, "lobby.no-template-for-type", type))
        );
    }

    /**
     * Lit la liste des templates publiés par Velocity depuis Redis.
     * Retourne une liste vide si aucun template n'est disponible.
     */
    public List<TemplateInfo> getCustomGameTemplates() {
        return templateCacheRef.get();
    }

    /**
     * Données immuables d'un template de serveur (publiées par Velocity).
     */
    public record TemplateInfo(String id, String name, String type, int maxPlayers) {}

    /**
     * Données immuables d'un serveur (snapshot Redis).
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
        public boolean isStarting() { return "STARTING".equalsIgnoreCase(status); }
        public boolean isPlaying() { return "PLAYING".equalsIgnoreCase(status); }
        public boolean isFull()     { return playerCount >= maxPlayers; }
        public String displayName() {
            return id == null || id.isBlank()
                    ? "Serveur"
                    : id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
        }
    }
}
