package fr.tropicube.core.managers;

import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.core.TropicubeCore;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * Central player data manager.
 * Load/save the full profile of each player.
 */
public class PlayerDataManager {

    public record PlayerProfile(
            UUID uuid, String username, String displayName,
            long firstJoin, long lastJoin, long playTime,
            String language, String grade,
            boolean banned, String banReason, long banExpiry
    ) {}

    private final TropicubeCore plugin;
    private final DatabaseManager db;
    private final PermissionManager permissionManager;
    private final EconomyManager economyManager;
    private final LanguageManager languageManager;

    private final Map<UUID, PlayerProfile> profileCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();
    // Mutes actifs : UUID -> expiry timestamp (-1 = permanent)
    private final Map<UUID, Long> activeMutes = new ConcurrentHashMap<>();

    public PlayerDataManager(TropicubeCore plugin, DatabaseManager db,
                             PermissionManager permissionManager, EconomyManager economyManager,
                             LanguageManager languageManager) {
        this.plugin = plugin;
        this.db = db;
        this.permissionManager = Objects.requireNonNull(permissionManager, "permissionManager");
        this.economyManager = Objects.requireNonNull(economyManager, "economyManager");
        this.languageManager = Objects.requireNonNull(languageManager, "languageManager");
    }

    public void initialize() {
        loadActiveMutes();
        // Periodic backup every 5 minutes
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                this::saveAll, 6000L, 6000L);
    }

    // ===== Player Loading =====

    public CompletableFuture<PlayerProfile> loadPlayer(Player player) {
        // Bukkit properties are captured before leaving the main thread.
        UUID uuid = player.getUniqueId();
        String username = player.getName();
        String displayName = player.getName();
        return CompletableFuture.supplyAsync(() -> {
            long now = System.currentTimeMillis();

            try (Connection conn = db.getConnection()) {
                // Check if existing player
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT * FROM tropicube_players WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();

                    PlayerProfile profile;
                    if (rs.next()) {
                        // Existing player — update last_join and username
                        profile = new PlayerProfile(
                                uuid, username,
                                rs.getString("display_name"),
                                rs.getLong("first_join"), now,
                                rs.getLong("play_time"),
                                rs.getString("language"),
                                rs.getString("grade"),
                                rs.getBoolean("is_banned"),
                                rs.getString("ban_reason"),
                                rs.getLong("ban_expiry")
                        );
                        db.executeUpdate(
                                "UPDATE tropicube_players SET username = ?, last_join = ? WHERE uuid = ?",
                                username, now, uuid.toString()
                        );
                    } else {
                        // New player
                        profile = new PlayerProfile(
                                uuid, username, displayName,
                                now, now, 0L, "fr", "JOUEUR",
                                false, null, 0L
                        );
                        db.executeUpdate(
                                "INSERT INTO tropicube_players (uuid, username, display_name, first_join, last_join, play_time, language, grade) " +
                                "VALUES (?, ?, ?, ?, ?, 0, 'fr', 'JOUEUR')",
                                uuid.toString(), username, displayName, now, now
                        );
                        economyManager.createAccount(uuid);
                        plugin.getLogger().info(MessageStyle.log("tc", "CORE", "<gray>Nouveau joueur : " + username));
                    }

                    profileCache.put(uuid, profile);
                    sessionStart.put(uuid, now);

                    // Load subsystems
                    languageManager.loadPlayerLanguage(uuid, profile.language());
                    permissionManager.loadPlayer(uuid);

                    return profile;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, MessageStyle.log("tc", "CORE", "<red>Erreur chargement joueur " + username), e);
                return null;
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        PlayerProfile profile = profileCache.remove(uuid);
        plugin.getRedisManager().removePlayerLanguage(String.valueOf(uuid));
        if (profile == null) return;

        Long start = sessionStart.remove(uuid);
        if (start != null) {
            long playTime = (System.currentTimeMillis() - start) / 1000;
            db.executeUpdate(
                    "UPDATE tropicube_players SET play_time = play_time + ?, last_join = ? WHERE uuid = ?",
                    playTime, System.currentTimeMillis(), uuid.toString()
            );
        }

        permissionManager.unloadPlayer(uuid);
        languageManager.unloadPlayer(uuid);
        economyManager.invalidateCache(uuid);
    }

    public void saveAll() {
        profileCache.keySet().forEach(uuid -> {
            Long start = sessionStart.get(uuid);
            if (start != null) {
                long playTime = (System.currentTimeMillis() - start) / 1000;
                db.executeUpdate(
                        "UPDATE tropicube_players SET play_time = play_time + ? WHERE uuid = ?",
                        playTime, uuid.toString()
                );
                sessionStart.put(uuid, System.currentTimeMillis());
            }
        });
    }

    // ===== Moderation =====

    public void mutePlayer(UUID target, long durationSeconds, String reason, UUID staffUuid, String staffName) {
        long expiry = durationSeconds <= 0 ? -1 : (System.currentTimeMillis() / 1000) + durationSeconds;
        activeMutes.put(target, expiry);

        db.executeUpdate(
                "INSERT INTO tropicube_sanctions (player_uuid, type, reason, staff_uuid, staff_name, timestamp, expiry) VALUES (?, 'MUTE', ?, ?, ?, ?, ?)",
                target.toString(), reason,
                staffUuid != null ? staffUuid.toString() : null,
                staffName, System.currentTimeMillis() / 1000, expiry
        );
    }

    public void unmute(UUID target) {
        activeMutes.remove(target);
        db.executeUpdate(
                "UPDATE tropicube_sanctions SET active = FALSE WHERE player_uuid = ? AND type = 'MUTE' AND active = TRUE",
                target.toString()
        );
    }

    public boolean isMuted(UUID uuid) {
        Long expiry = activeMutes.get(uuid);
        if (expiry == null) return false;
        if (expiry == -1) return true;
        if (expiry <= System.currentTimeMillis() / 1000) {
            activeMutes.remove(uuid);
            return false;
        }
        return true;
    }

    public long getMuteExpiry(UUID uuid) {
        return activeMutes.getOrDefault(uuid, -1L);
    }

    public void addWarn(UUID target, String reason, UUID staffUuid, String staffName) {
        db.executeUpdate(
                "INSERT INTO tropicube_sanctions (player_uuid, type, reason, staff_uuid, staff_name, timestamp) VALUES (?, 'WARN', ?, ?, ?, ?)",
                target.toString(), reason,
                staffUuid != null ? staffUuid.toString() : null,
                staffName, System.currentTimeMillis() / 1000
        );
    }

    public int getWarnCount(UUID uuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM tropicube_sanctions WHERE player_uuid = ? AND type = 'WARN' AND active = TRUE")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, MessageStyle.log("tc", "PLAYER_DATA", "<yellow>Erreur getWarnCount"), e);
        }
        return 0;
    }

    public List<Map<String, Object>> getSanctionHistory(UUID uuid) {
        List<Map<String, Object>> history = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM tropicube_sanctions WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT 20")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("type", rs.getString("type"));
                entry.put("reason", rs.getString("reason"));
                entry.put("staff", rs.getString("staff_name"));
                entry.put("timestamp", rs.getLong("timestamp"));
                entry.put("expiry", rs.getLong("expiry"));
                entry.put("active", rs.getBoolean("active"));
                history.add(entry);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, MessageStyle.log("tc", "PLAYER_DATA", "<yellow>Erreur historique sanctions"), e);
        }
        return history;
    }

    private void loadActiveMutes() {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT player_uuid, expiry FROM tropicube_sanctions " +
                     "WHERE type = 'MUTE' AND active = TRUE AND (expiry = -1 OR expiry > ?)")) {
            stmt.setLong(1, System.currentTimeMillis() / 1000);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                activeMutes.put(UUID.fromString(rs.getString("player_uuid")), rs.getLong("expiry"));
            }
            plugin.getLogger().info(MessageStyle.log("tc", "CORE", "<gray>" + activeMutes.size() + " mutes actifs chargés."));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, MessageStyle.log("tc", "PLAYER_DATA", "<yellow>Erreur chargement mutes"), e);
        }
    }

    // ===== Offline player search =====

    public Optional<UUID> getUuidByName(String name) {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT uuid FROM tropicube_players WHERE username = ?")) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return Optional.of(UUID.fromString(rs.getString("uuid")));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, MessageStyle.log("tc", "PLAYER_DATA", "<yellow>Erreur recherche UUID " + name), e);
        }
        return Optional.empty();
    }

    public Optional<PlayerProfile> getProfile(UUID uuid) {
        return Optional.ofNullable(profileCache.get(uuid));
    }

    public Map<UUID, Long> getActiveMutes() { return Collections.unmodifiableMap(activeMutes); }
}
