package fr.tropicube.core.managers;

import fr.tropicube.core.TropicubeCore;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * Gestionnaire des permissions et grades Tropicube.
 * <p>
 * Hiérarchie des grades (ordre croissant) :
 *   JOUEUR → VIP → VIP+ → PREMIUM → HELPER → MODERATEUR → ADMIN → OWNER
 */
public class PermissionManager {

    public record Grade(String name, String displayName, String prefix, String suffix,
                        String color, int priority, boolean isVip, boolean isStaff,
                        Set<String> permissions) {}

    private final TropicubeCore plugin;
    private final DatabaseManager db;

    // Cache des grades définis
    private final Map<String, Grade> gradeRegistry = new ConcurrentHashMap<>();
    // Grade de chaque joueur : UUID -> grade name
    private final Map<UUID, String> playerGrades = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerGradeExpiries = new ConcurrentHashMap<>();
    // Permissions individuelles : UUID -> Set<permission>
    private final Map<UUID, Set<String>> playerPermissions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> playerPermissionExpiries = new ConcurrentHashMap<>();
    // Attachments Bukkit : UUID -> PermissionAttachment
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public PermissionManager(TropicubeCore plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void initialize() throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM tropicube_grades ORDER BY priority ASC");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Set<String> perms = ConcurrentHashMap.newKeySet();
                String permStr = rs.getString("permissions");
                if (permStr != null && !permStr.isEmpty()) {
                    perms.addAll(Arrays.asList(permStr.split(",")));
                }
                Grade grade = new Grade(
                        rs.getString("name"),
                        rs.getString("display_name"),
                        rs.getString("prefix"),
                        rs.getString("suffix"),
                        rs.getString("color"),
                        rs.getInt("priority"),
                        rs.getBoolean("is_vip"),
                        rs.getBoolean("is_staff"),
                        perms
                );
                gradeRegistry.put(grade.name(), grade);
            }
            plugin.getLogger().info("[Tropicube-Perms] " + gradeRegistry.size() + " grades chargés.");
        }
    }

    // ===== Chargement joueur =====

    public void loadPlayer(UUID uuid) {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = db.getConnection()) {
                // Charger le grade
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT grade, grade_expiry FROM tropicube_players WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        String grade = rs.getString("grade");
                        long expiry = rs.getLong("grade_expiry");
                        long now = System.currentTimeMillis() / 1000;
                        if (expiry > 0 && expiry <= now) {
                            grade = "JOUEUR";
                            expiry = -1;
                            db.executeUpdate("UPDATE tropicube_players SET grade = 'JOUEUR', grade_expiry = -1 WHERE uuid = ?",
                                    uuid.toString());
                        }
                        playerGrades.put(uuid, grade);
                        playerGradeExpiries.put(uuid, expiry);
                        scheduleGradeExpiry(uuid, expiry);
                    } else {
                        playerGrades.put(uuid, "JOUEUR");
                        playerGradeExpiries.put(uuid, -1L);
                    }
                }

                // Charger les permissions individuelles
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT permission, value, expiry FROM tropicube_permissions WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    Set<String> perms = ConcurrentHashMap.newKeySet();
                    Map<String, Long> expiries = new ConcurrentHashMap<>();
                    long now = System.currentTimeMillis() / 1000;
                    while (rs.next()) {
                        long expiry = rs.getLong("expiry");
                        if (expiry == -1 || expiry > now) {
                            if (rs.getBoolean("value")) {
                                String permission = rs.getString("permission");
                                perms.add(permission);
                                expiries.put(permission, expiry);
                            }
                        }
                    }
                    playerPermissions.put(uuid, perms);
                    playerPermissionExpiries.put(uuid, expiries);
                    expiries.forEach((permission, expiry) -> schedulePermissionExpiry(uuid, permission, expiry));
                }
                db.executeUpdate("DELETE FROM tropicube_permissions WHERE uuid = ? AND expiry > 0 AND expiry <= ?",
                        uuid.toString(), System.currentTimeMillis() / 1000);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[Tropicube-Perms] Erreur chargement joueur " + uuid, e);
            }

            // Appliquer les permissions en synchrone sur le thread Bukkit
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null) applyPermissions(player);
            });
        });
    }

    public void unloadPlayer(UUID uuid) {
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) player.removeAttachment(attachment);
        }
        playerGrades.remove(uuid);
        playerGradeExpiries.remove(uuid);
        playerPermissions.remove(uuid);
        playerPermissionExpiries.remove(uuid);
    }

    // ===== Application des permissions =====

    private void applyPermissions(Player player) {
        UUID uuid = player.getUniqueId();

        // Retirer l'ancien attachment
        PermissionAttachment old = attachments.remove(uuid);
        if (old != null) player.removeAttachment(old);

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(uuid, attachment);

        // Appliquer les permissions du grade
        String gradeName = playerGrades.getOrDefault(uuid, "JOUEUR");
        Grade grade = gradeRegistry.get(gradeName);
        if (grade != null) {
            grade.permissions().forEach(perm -> applyPermissionPattern(attachment, perm));
        }

        // Appliquer les permissions individuelles
        Set<String> individual = playerPermissions.getOrDefault(uuid, Collections.emptySet());
        individual.forEach(perm -> applyPermissionPattern(attachment, perm));

        player.recalculatePermissions();
    }

    // ===== Grade =====

    public String getGrade(UUID uuid) {
        String cached = playerGrades.get(uuid);
        if (cached != null) return cached;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT grade, grade_expiry FROM tropicube_players WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return "JOUEUR";
                long expiry = rs.getLong("grade_expiry");
                if (expiry > 0 && expiry <= System.currentTimeMillis() / 1000) return "JOUEUR";
                return rs.getString("grade");
            }
        } catch (SQLException e) {
            throw new DatabaseManager.DatabaseOperationException("Impossible de charger le grade", e);
        }
    }

    public Grade getGradeInfo(UUID uuid) {
        return gradeRegistry.getOrDefault(getGrade(uuid), gradeRegistry.get("JOUEUR"));
    }

    public void setGrade(UUID uuid, String gradeName, long expirySeconds) {
        if (!gradeRegistry.containsKey(gradeName)) return;

        long expiryEpoch = expirySeconds > 0 ? Math.addExact(System.currentTimeMillis() / 1000, expirySeconds) : -1;
        playerGrades.put(uuid, gradeName);
        playerGradeExpiries.put(uuid, expiryEpoch);
        db.executeUpdate("UPDATE tropicube_players SET grade = ?, grade_expiry = ? WHERE uuid = ?",
                gradeName, expiryEpoch, uuid.toString());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                applyPermissions(player);
                player.sendMessage(plugin.getLanguageManager().getComponent(uuid, "grade.set-self",
                        gradeRegistry.get(gradeName).prefix() + gradeRegistry.get(gradeName).displayName()));
            }
        });

        // Grade temporaire
        scheduleGradeExpiry(uuid, expiryEpoch);
    }

    public boolean isVip(UUID uuid) {
        Grade grade = getGradeInfo(uuid);
        return grade != null && grade.isVip();
    }

    public boolean isStaff(UUID uuid) {
        Grade grade = getGradeInfo(uuid);
        return grade != null && grade.isStaff();
    }

    public int getPriority(UUID uuid) {
        Grade grade = getGradeInfo(uuid);
        return grade != null ? grade.priority() : 0;
    }

    // ===== Permissions individuelles =====

    public void addPermission(UUID uuid, String permission, long durationSeconds, UUID grantedBy) {
        long expiryEpoch = durationSeconds > 0
                ? Math.addExact(System.currentTimeMillis() / 1000, durationSeconds) : -1;
        playerPermissions.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(permission);
        playerPermissionExpiries.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(permission, expiryEpoch);

        db.executeUpdate(
                "INSERT INTO tropicube_permissions (uuid, permission, value, expiry, granted_by) VALUES (?, ?, TRUE, ?, ?) " +
                "ON DUPLICATE KEY UPDATE value = TRUE, expiry = ?, granted_by = ?",
                uuid.toString(), permission, expiryEpoch,
                grantedBy != null ? grantedBy.toString() : null,
                expiryEpoch, grantedBy != null ? grantedBy.toString() : null
        );

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) applyPermissions(player);
        });
        schedulePermissionExpiry(uuid, permission, expiryEpoch);
    }

    public void removePermission(UUID uuid, String permission) {
        Set<String> perms = playerPermissions.get(uuid);
        if (perms != null) perms.remove(permission);
        Map<String, Long> expiries = playerPermissionExpiries.get(uuid);
        if (expiries != null) expiries.remove(permission);

        db.executeUpdate("DELETE FROM tropicube_permissions WHERE uuid = ? AND permission = ?",
                uuid.toString(), permission);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) applyPermissions(player);
        });
    }

    public boolean hasPermission(UUID uuid, String permission) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) return player.hasPermission(permission);

        // Vérification hors-ligne
        Grade grade = getGradeInfo(uuid);
        if (grade != null && grade.permissions().stream().anyMatch(pattern -> matches(pattern, permission))) return true;
        Set<String> individual = getIndividualPermissions(uuid);
        return individual.stream().anyMatch(pattern -> matches(pattern, permission));
    }

    // ===== Formatage =====

    public String getFormattedName(UUID uuid, String username) {
        Grade grade = getGradeInfo(uuid);
        if (grade == null) return "<white>" + username;
        return grade.prefix() + grade.color() + username;
    }

    public String getPrefix(UUID uuid) {
        Grade grade = getGradeInfo(uuid);
        return grade != null ? grade.prefix() : "";
    }

    public Map<String, Grade> getAllGrades() { return Collections.unmodifiableMap(gradeRegistry); }

    // ===== Grade permissions =====

    public void addGradePermission(String gradeName, String permission) {
        Grade grade = gradeRegistry.get(gradeName);
        if (grade == null) return;
        grade.permissions().add(permission.trim());
        persistGradePermissions(gradeName);
    }

    public void removeGradePermission(String gradeName, String permission) {
        Grade grade = gradeRegistry.get(gradeName);
        if (grade == null) return;
        grade.permissions().remove(permission.trim());
        persistGradePermissions(gradeName);
    }

    public Set<String> getGradePermissions(String gradeName) {
        Grade grade = gradeRegistry.get(gradeName);
        return grade != null ? Collections.unmodifiableSet(grade.permissions()) : Collections.emptySet();
    }

    public Set<String> getIndividualPermissions(UUID uuid) {
        Set<String> cached = playerPermissions.get(uuid);
        if (cached != null) return Collections.unmodifiableSet(cached);
        Set<String> loaded = ConcurrentHashMap.newKeySet();
        long now = System.currentTimeMillis() / 1000;
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT permission FROM tropicube_permissions WHERE uuid = ? AND value = TRUE AND (expiry = -1 OR expiry > ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, now);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) loaded.add(rs.getString(1));
            }
            return Collections.unmodifiableSet(loaded);
        } catch (SQLException e) {
            throw new DatabaseManager.DatabaseOperationException("Impossible de charger les permissions", e);
        }
    }

    private void persistGradePermissions(String gradeName) {
        Grade grade = gradeRegistry.get(gradeName);
        if (grade == null) return;
        String permsStr = String.join(",", grade.permissions());
        db.executeUpdate("UPDATE tropicube_grades SET permissions = ? WHERE name = ?", permsStr, gradeName);
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> gradeName.equals(playerGrades.get(p.getUniqueId())))
                        .forEach(this::applyPermissions));
    }

    public void reload() {
        gradeRegistry.clear();
        try {
            initialize();
        } catch (SQLException e) {
            throw new DatabaseManager.DatabaseOperationException("Impossible de recharger les permissions", e);
        }
        Runnable reapply = () -> plugin.getServer().getOnlinePlayers()
                .forEach(p -> loadPlayer(p.getUniqueId()));
        if (plugin.getServer().isPrimaryThread()) reapply.run();
        else plugin.getServer().getScheduler().runTask(plugin, reapply);
    }

    private void applyPermissionPattern(PermissionAttachment attachment, String rawPattern) {
        String pattern = rawPattern == null ? "" : rawPattern.trim();
        if (pattern.isEmpty()) return;
        if (!pattern.endsWith("*")) {
            attachment.setPermission(pattern, true);
            return;
        }
        String prefix = pattern.substring(0, pattern.length() - 1);
        plugin.getServer().getPluginManager().getPermissions().stream()
                .map(org.bukkit.permissions.Permission::getName)
                .filter(name -> prefix.isEmpty() || name.startsWith(prefix))
                .forEach(name -> attachment.setPermission(name, true));
    }

    private boolean matches(String pattern, String permission) {
        if (pattern == null) return false;
        String trimmed = pattern.trim();
        if (trimmed.equals("*")) return true;
        return trimmed.endsWith("*")
                ? permission.startsWith(trimmed.substring(0, trimmed.length() - 1))
                : trimmed.equalsIgnoreCase(permission);
    }

    private void scheduleGradeExpiry(UUID uuid, long expiryEpoch) {
        if (expiryEpoch <= 0) return;
        long delaySeconds = Math.max(1, expiryEpoch - System.currentTimeMillis() / 1000);
        long ticks = Math.multiplyExact(delaySeconds, 20L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (playerGradeExpiries.getOrDefault(uuid, -1L) != expiryEpoch) return;
            setGrade(uuid, "JOUEUR", -1);
        }, ticks);
    }

    private void schedulePermissionExpiry(UUID uuid, String permission, long expiryEpoch) {
        if (expiryEpoch <= 0) return;
        long delaySeconds = Math.max(1, expiryEpoch - System.currentTimeMillis() / 1000);
        long ticks = Math.multiplyExact(delaySeconds, 20L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Map<String, Long> expiries = playerPermissionExpiries.get(uuid);
            if (expiries == null || expiries.getOrDefault(permission, -1L) != expiryEpoch) return;
            removePermission(uuid, permission);
        }, ticks);
    }
}
