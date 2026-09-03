package fr.tropicube.core.managers;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.core.events.PlayerAccessChangedEvent;
import fr.tropicube.docker.model.AccessPolicy;
import fr.tropicube.docker.model.PlayerAccessProfile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Cosmetic grade catalog and sole owner of cumulative VIP/moderation access. */
public class PermissionManager {
    public record Grade(String name, String displayName, String prefix, String suffix,
                        String color, int priority, int defaultVipLevel, int defaultModLevel) {
    }
    public enum Axis { VIP, MOD }

    private final TropicubeCore plugin;
    private final DatabaseManager db;
    private final Map<String, Grade> gradeRegistry = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerGrades = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerGradeExpiries = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerAccessProfile> accessProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final DisplayGradeOverrideCache displayGradeOverrides = new DisplayGradeOverrideCache();
    private AccessPolicy accessPolicy = AccessPolicy.defaults();

    public PermissionManager(TropicubeCore plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void initialize() throws SQLException {
        accessPolicy = new AccessPolicy(configuredThresholds("access.permission-thresholds.vip"),
                configuredThresholds("access.permission-thresholds.mod"));
        gradeRegistry.clear();
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT name,display_name,prefix,suffix,color,priority,default_vip_level,default_mod_level FROM tropicube_grades ORDER BY priority");
             ResultSet r = s.executeQuery()) {
            while (r.next()) {
                Grade grade = new Grade(r.getString(1), r.getString(2), r.getString(3), r.getString(4),
                        r.getString(5), r.getInt(6), r.getInt(7), r.getInt(8));
                new PlayerAccessProfile(grade.defaultVipLevel(), grade.defaultModLevel(), 0);
                gradeRegistry.put(grade.name(), grade);
            }
        }
        CompletableFuture.runAsync(this::preloadRedisProfiles);
        plugin.getLogger().info(MessageStyle.log("tc", "ACCESS", "<gray>" + gradeRegistry.size()
                + " grades cosmétiques chargés."));
    }

    private void preloadRedisProfiles() {
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT uuid,vip_level,mod_level,access_revision FROM tropicube_players"); ResultSet r = s.executeQuery()) {
            while (r.next()) publishProfile(UUID.fromString(r.getString(1)),
                    new PlayerAccessProfile(r.getInt(2), r.getInt(3), r.getLong(4)), false);
            plugin.getRedisManager().publishPlayerEvent("ACCESS_SNAPSHOT_READY",
                    Long.toString(System.currentTimeMillis()));
        } catch (SQLException | RuntimeException error) {
            plugin.getLogger().log(Level.SEVERE,
                    MessageStyle.log("tc", "ACCESS", "<red>Préchargement Redis des accès impossible"), error);
        }
    }

    public void loadPlayer(UUID uuid) {
        CompletableFuture.runAsync(() -> {
            try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                    "SELECT grade,grade_expiry,vip_level,mod_level,access_revision FROM tropicube_players WHERE uuid=?")) {
                s.setString(1, uuid.toString());
                try (ResultSet r = s.executeQuery()) {
                    if (!r.next()) return;
                    String grade = r.getString(1);
                    long expiry = r.getLong(2);
                    if (expiry > 0 && expiry <= System.currentTimeMillis() / 1000) {
                        setGrade(uuid, "JOUEUR", -1, null, "GRADE_EXPIRY");
                        return;
                    }
                    cacheGradeDisplay(uuid, grade);
                    playerGradeExpiries.put(uuid, expiry);
                    PlayerAccessProfile profile = new PlayerAccessProfile(r.getInt(3), r.getInt(4), r.getLong(5));
                    accessProfiles.put(uuid, profile);
                    publishProfile(uuid, profile, true);
                    plugin.getRedisManager().publishPlayerEvent("GRADE_LOADED", uuid.toString());
                    scheduleGradeExpiry(uuid, expiry);
                }
            } catch (SQLException | RuntimeException error) {
                plugin.getLogger().log(Level.SEVERE,
                        MessageStyle.log("tc", "ACCESS", "<red>Erreur chargement joueur " + uuid), error);
            }
            runSync(() -> Optional.ofNullable(plugin.getServer().getPlayer(uuid)).ifPresent(this::applyPermissions));
        });
    }

    public void unloadPlayer(UUID uuid) {
        PermissionAttachment attachment = attachments.remove(uuid);
        Player player = plugin.getServer().getPlayer(uuid);
        if (attachment != null && player != null) player.removeAttachment(attachment);
        playerGrades.remove(uuid);
        playerGradeExpiries.remove(uuid);
        accessProfiles.remove(uuid);
        displayGradeOverrides.remove(uuid);
    }

    private void applyPermissions(Player player) {
        PermissionAttachment previous = attachments.remove(player.getUniqueId());
        if (previous != null) player.removeAttachment(previous);
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), attachment);
        PlayerAccessProfile profile = getCachedAccessProfile(player.getUniqueId());
        plugin.getServer().getPluginManager().getPermissions().forEach(permission ->
                attachment.setPermission(permission.getName(), accessPolicy.hasPermission(profile, permission.getName())));
        if (profile.modLevel() >= 4) attachment.setPermission("*", true);
        player.recalculatePermissions();
        player.updateCommands();
        plugin.getServer().getPluginManager().callEvent(new PlayerAccessChangedEvent(player, profile));
    }

    public PlayerAccessProfile getAccessProfile(UUID uuid) {
        PlayerAccessProfile cached = accessProfiles.get(uuid);
        if (cached != null) return cached;
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT vip_level,mod_level,access_revision FROM tropicube_players WHERE uuid=?")) {
            s.setString(1, uuid.toString());
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new PlayerAccessProfile(r.getInt(1), r.getInt(2), r.getLong(3))
                        : PlayerAccessProfile.none();
            }
        } catch (SQLException error) {
            throw new DatabaseManager.DatabaseOperationException("Impossible de charger le profil d'accès", error);
        }
    }

    /** Returns the local snapshot without performing SQL on the server thread. */
    public PlayerAccessProfile getCachedAccessProfile(UUID uuid) {
        return accessProfiles.getOrDefault(uuid, PlayerAccessProfile.none());
    }

    public int getVipLevel(UUID uuid) { return getCachedAccessProfile(uuid).vipLevel(); }
    public int getModLevel(UUID uuid) { return getCachedAccessProfile(uuid).modLevel(); }
    public boolean hasPermission(UUID uuid, String permission) {
        return accessPolicy.hasPermission(getCachedAccessProfile(uuid), permission);
    }

    public void setAccessLevel(UUID uuid, Axis axis, int level, UUID actor) {
        int maximum = axis == Axis.VIP ? PlayerAccessProfile.MAX_VIP_LEVEL : PlayerAccessProfile.MAX_MOD_LEVEL;
        if (level < 0 || level > maximum) throw new IllegalArgumentException("Niveau hors limites");
        mutate(uuid, actor, "COMMAND_LEVEL", null, -1, axis, level);
    }

    public String getGrade(UUID uuid) {
        String cached = playerGrades.get(uuid);
        if (cached != null) return cached;
        try (Connection c = db.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT grade FROM tropicube_players WHERE uuid=?")) {
            s.setString(1, uuid.toString());
            try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : "JOUEUR"; }
        } catch (SQLException error) {
            throw new DatabaseManager.DatabaseOperationException("Impossible de charger le grade", error);
        }
    }

    public Grade getGradeInfo(UUID uuid) {
        return gradeRegistry.getOrDefault(getGrade(uuid), gradeRegistry.get("JOUEUR"));
    }

    /** Returns the configured grade prefix only, without a username and without performing SQL. */
    public String getCachedGradeDisplay(UUID uuid) {
        Grade grade = gradeRegistry.getOrDefault(playerGrades.get(uuid), gradeRegistry.get("JOUEUR"));
        return formatGradeDisplay(grade);
    }

    static String formatGradeDisplay(Grade grade) {
        return grade == null ? "" : grade.prefix().strip();
    }

    public void setGrade(UUID uuid, String gradeName, long durationSeconds) {
        setGrade(uuid, gradeName, durationSeconds, null, "COMMAND_GRADE");
    }

    public void setGrade(UUID uuid, String gradeName, long durationSeconds, UUID actor, String source) {
        if (!gradeRegistry.containsKey(gradeName)) throw new IllegalArgumentException("Grade inconnu: " + gradeName);
        long expiry = durationSeconds > 0 ? Math.addExact(System.currentTimeMillis() / 1000, durationSeconds) : -1;
        mutate(uuid, actor, source, gradeName, expiry, null, -1);
        scheduleGradeExpiry(uuid, expiry);
        runSync(() -> Optional.ofNullable(plugin.getServer().getPlayer(uuid)).ifPresent(player ->
                player.sendMessage(plugin.getLanguageManager().getComponent(uuid, "grade.set-self",
                        gradeRegistry.get(gradeName).prefix() + gradeRegistry.get(gradeName).displayName()))));
    }

    private void mutate(UUID uuid, UUID actor, String source, String requestedGrade, long expiry,
                        Axis axis, int requestedLevel) {
        PlayerAccessProfile updated;
        String newGrade;
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement lock = c.prepareStatement(
                        "SELECT grade,grade_expiry,vip_level,mod_level,access_revision FROM tropicube_players WHERE uuid=? FOR UPDATE")) {
                    lock.setString(1, uuid.toString());
                    try (ResultSet r = lock.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Joueur introuvable");
                        String oldGrade = r.getString(1);
                        long oldExpiry = r.getLong(2);
                        int oldVip = r.getInt(3), oldMod = r.getInt(4);
                        long revision = r.getLong(5) + 1;
                        newGrade = requestedGrade == null ? oldGrade : requestedGrade;
                        int newVip = oldVip, newMod = oldMod;
                        if (requestedGrade != null) {
                            Grade grade = gradeRegistry.get(requestedGrade);
                            newVip = grade.defaultVipLevel();
                            newMod = grade.defaultModLevel();
                        } else if (axis == Axis.VIP) newVip = requestedLevel;
                        else newMod = requestedLevel;
                        updated = new PlayerAccessProfile(newVip, newMod, revision);
                        try (PreparedStatement update = c.prepareStatement(
                                "UPDATE tropicube_players SET grade=?,grade_expiry=?,vip_level=?,mod_level=?,access_revision=? WHERE uuid=?")) {
                            update.setString(1, newGrade);
                            update.setLong(2, requestedGrade == null ? oldExpiry : expiry);
                            update.setInt(3, newVip); update.setInt(4, newMod); update.setLong(5, revision);
                            update.setString(6, uuid.toString()); update.executeUpdate();
                        }
                        insertAudit(c, uuid, actor, source, oldGrade, newGrade,
                                oldVip, newVip, oldMod, newMod, revision);
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException failure) {
                c.rollback();
                throw failure;
            } finally { c.setAutoCommit(true); }
        } catch (SQLException error) {
            throw new DatabaseManager.DatabaseOperationException("Impossible de modifier le profil d'accès", error);
        }
        cacheGradeDisplay(uuid, newGrade);
        if (requestedGrade != null) playerGradeExpiries.put(uuid, expiry);
        accessProfiles.put(uuid, updated);
        publishProfile(uuid, updated, true);
        if (requestedGrade != null) {
            plugin.getRedisManager().publishPlayerEvent("GRADE_CHANGED", uuid.toString());
        }
        runSync(() -> Optional.ofNullable(plugin.getServer().getPlayer(uuid)).ifPresent(this::applyPermissions));
    }

    public static void insertAudit(Connection c, UUID target, UUID actor, String source,
                                   String oldGrade, String newGrade, int oldVip, int newVip,
                                   int oldMod, int newMod, long revision) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO tropicube_access_audit(target_uuid,actor_uuid,source,previous_grade,new_grade,
                previous_vip_level,new_vip_level,previous_mod_level,new_mod_level,revision,changed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            s.setString(1, target.toString()); s.setString(2, actor == null ? null : actor.toString());
            s.setString(3, source); s.setString(4, oldGrade); s.setString(5, newGrade);
            s.setInt(6, oldVip); s.setInt(7, newVip); s.setInt(8, oldMod); s.setInt(9, newMod);
            s.setLong(10, revision); s.setLong(11, System.currentTimeMillis() / 1000); s.executeUpdate();
        }
    }

    private void publishProfile(UUID uuid, PlayerAccessProfile profile, boolean event) {
        plugin.getRedisManager().setPersistent(PlayerAccessProfile.key(uuid), profile.serialize());
        if (event) plugin.getRedisManager().publishPlayerEvent("ACCESS_CHANGED", uuid + ":" + profile.serialize());
    }

    private void cacheGradeDisplay(UUID uuid, String gradeName) {
        playerGrades.put(uuid, gradeName);
        String display = formatGradeDisplay(gradeRegistry.get(gradeName));
        if (!display.isBlank()) plugin.getRedisManager().setPlayerGradeDisplay(uuid.toString(), display);
    }

    /** Refreshes local state after a purchase transaction committed a complete profile. */
    public void applyCommittedGrade(UUID uuid, String ignoredGradeName) { loadPlayer(uuid); }
    public String getFormattedName(UUID uuid, String username) {
        Grade g = getGradeInfo(uuid); return g == null ? "<white>" + username : g.prefix() + g.color() + username;
    }
    public Optional<String> getCachedDisplayFormattedName(UUID uuid, String username) {
        var identity = displayGradeOverrides.resolve(uuid, username, playerGrades.get(uuid));
        return identity.gradeName() == null ? Optional.empty()
                : Optional.of(formatName(gradeRegistry, identity.gradeName(), identity.name()));
    }
    public void setDisplayIdentityOverride(UUID uuid, String displayName, String gradeName) {
        displayGradeOverrides.put(uuid, displayName, gradeName);
    }
    public void clearDisplayIdentityOverride(UUID uuid) { displayGradeOverrides.remove(uuid); }
    static String formatName(Map<String, Grade> grades, String gradeName, String username) {
        Grade g = grades.get(gradeName); return g == null ? "<white>" + username : g.prefix() + g.color() + username;
    }
    public Map<String, Grade> getAllGrades() { return Collections.unmodifiableMap(gradeRegistry); }

    public void purgeAudit(int retentionDays) {
        if (retentionDays < 1) throw new IllegalArgumentException("La rétention d'audit doit être positive");
        db.executeUpdate("DELETE FROM tropicube_access_audit WHERE changed_at < ?",
                System.currentTimeMillis() / 1000 - retentionDays * 86_400L);
    }

    public void reload() {
        try { initialize(); }
        catch (SQLException error) { throw new DatabaseManager.DatabaseOperationException("Rechargement impossible", error); }
        runSync(() -> plugin.getServer().getOnlinePlayers().forEach(player -> loadPlayer(player.getUniqueId())));
    }

    private void scheduleGradeExpiry(UUID uuid, long expiry) {
        if (expiry <= 0) return;
        long delay = Math.max(1, expiry - System.currentTimeMillis() / 1000);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (playerGradeExpiries.getOrDefault(uuid, -1L) == expiry) {
                CompletableFuture.runAsync(() -> setGrade(uuid, "JOUEUR", -1, null, "GRADE_EXPIRY"))
                        .exceptionally(error -> {
                            plugin.getLogger().log(Level.SEVERE, MessageStyle.log("tc", "ACCESS",
                                    "<red>Expiration du grade impossible pour " + uuid), error);
                            return null;
                        });
            }
        }, Math.min(Long.MAX_VALUE / 2, delay * 20L));
    }
    private void runSync(Runnable action) {
        if (plugin.getServer().isPrimaryThread()) action.run();
        else plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private Map<String, Integer> configuredThresholds(String path) {
        var section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) return Map.of();
        Map<String, Integer> values = new HashMap<>();
        section.getValues(true).forEach((permission, value) -> {
            if (value instanceof ConfigurationSection) return;
            if (!(value instanceof Number number)) throw new IllegalArgumentException(path + "." + permission
                    + " doit être un entier");
            values.put(permission, number.intValue());
        });
        return values;
    }
}
