package fr.tropicube.core.network;

import com.google.gson.Gson;
import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.NetworkChatMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Authoritative bans, report workflow and short-lived chat evidence capture. */
public final class ModerationService {
    public static final int CHAT_BUFFER_SECONDS = 15 * 60;
    public static final long EVIDENCE_RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000;
    private static final Gson GSON = new Gson();

    public record ReportSummary(long id, UUID reporterId, UUID targetId, String category,
                                String status, UUID assignedStaffId, long createdAt) {}

    private final DatabaseManager database;
    private final RedisManager redis;

    public ModerationService(DatabaseManager database, RedisManager redis) {
        this.database = database;
        this.redis = redis;
    }

    public CompletableFuture<Void> ban(UUID targetId, String targetName, long durationSeconds,
                                       String reason, UUID staffId, String staffName) {
        return database.supplyAsync(() -> {
            long nowSeconds = System.currentTimeMillis() / 1000;
            long expiry = durationSeconds <= 0 ? -1 : Math.addExact(nowSeconds, durationSeconds);
            database.executeUpdate("UPDATE tropicube_players SET is_banned = TRUE, ban_reason = ?, ban_expiry = ? WHERE uuid = ?",
                    reason, expiry, targetId.toString());
            database.executeUpdate("""
                    INSERT INTO tropicube_sanctions
                        (player_uuid, type, reason, staff_uuid, staff_name, timestamp, expiry, active)
                    VALUES (?, 'BAN', ?, ?, ?, ?, ?, TRUE)
                    """, targetId.toString(), reason, staffId == null ? null : staffId.toString(), staffName,
                    nowSeconds, expiry);
            String payload = GSON.toJson(new BanState(targetName, reason, expiry));
            if (expiry < 0) redis.setPersistent("ban:" + targetId, payload);
            else redis.set("ban:" + targetId, payload, Math.max(1, Math.toIntExact(Math.min(Integer.MAX_VALUE, durationSeconds))));
            redis.publishCommand("PROXY", "BAN_ENFORCE:" + targetId);
            return null;
        });
    }

    public CompletableFuture<Void> unban(UUID targetId) {
        return database.supplyAsync(() -> {
            database.executeUpdate("UPDATE tropicube_players SET is_banned = FALSE, ban_reason = NULL, ban_expiry = 0 WHERE uuid = ?",
                    targetId.toString());
            database.executeUpdate("UPDATE tropicube_sanctions SET active = FALSE WHERE player_uuid = ? AND type = 'BAN' AND active = TRUE",
                    targetId.toString());
            redis.delete("ban:" + targetId);
            return null;
        });
    }

    /** Rebuilds the proxy cache from the authoritative player row after a cache loss. */
    public void cacheBan(UUID targetId, String targetName, String reason, long expiryEpochSecond) {
        String payload = GSON.toJson(new BanState(targetName, reason, expiryEpochSecond));
        if (expiryEpochSecond <= 0) {
            redis.setPersistent("ban:" + targetId, payload);
            return;
        }
        long remaining = expiryEpochSecond - System.currentTimeMillis() / 1000;
        if (remaining > 0) redis.set("ban:" + targetId, payload,
                Math.max(1, Math.toIntExact(Math.min(Integer.MAX_VALUE, remaining))));
    }

    public CompletableFuture<Long> report(UUID reporterId, UUID targetId, String category,
                                          String details, String instanceId, String messageId) {
        return database.supplyAsync(() -> createReport(reporterId, targetId, category, details, instanceId, messageId));
    }

    public CompletableFuture<List<ReportSummary>> openReports(int limit) {
        return database.supplyAsync(() -> loadOpenReports(Math.max(1, Math.min(limit, 100))));
    }

    public CompletableFuture<Boolean> claim(long reportId, UUID staffId) {
        return database.supplyAsync(() -> database.executeUpdate("""
                UPDATE tropicube_reports SET status = 'CLAIMED', assigned_staff_uuid = ?
                WHERE id = ? AND status = 'OPEN'
                """, staffId.toString(), reportId) == 1);
    }

    public CompletableFuture<Boolean> resolve(long reportId, UUID staffId, String resolution) {
        return database.supplyAsync(() -> database.executeUpdate("""
                UPDATE tropicube_reports SET status = 'RESOLVED', assigned_staff_uuid = ?,
                    resolution = ?, resolved_at = ?
                WHERE id = ? AND status IN ('OPEN', 'CLAIMED')
                """, staffId.toString(), resolution, System.currentTimeMillis(), reportId) == 1);
    }

    public CompletableFuture<Integer> purgeExpiredEvidence() {
        return database.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         DELETE evidence FROM tropicube_report_evidence evidence
                         JOIN tropicube_reports report ON report.id = evidence.report_id
                         WHERE report.evidence_expires_at <= ?
                         """)) {
                statement.setLong(1, System.currentTimeMillis());
                return statement.executeUpdate();
            }
        });
    }

    private long createReport(UUID reporterId, UUID targetId, String category, String details,
                              String instanceId, String messageId) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long reportId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO tropicube_reports
                            (reporter_uuid, target_uuid, category, details, instance_id, status,
                             created_at, evidence_expires_at)
                        VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, reporterId.toString());
                    statement.setString(2, targetId.toString());
                    statement.setString(3, category);
                    statement.setString(4, details);
                    statement.setString(5, instanceId);
                    statement.setLong(6, now);
                    statement.setLong(7, now + EVIDENCE_RETENTION_MILLIS);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Identifiant de signalement absent");
                        reportId = keys.getLong(1);
                    }
                }
                if (messageId != null && !messageId.isBlank()) attachEvidence(connection, reportId, targetId, messageId, now);
                connection.commit();
                return reportId;
            } catch (Exception error) {
                connection.rollback();
                if (error instanceof SQLException sql) throw sql;
                throw new SQLException("Impossible de créer le signalement", error);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void attachEvidence(Connection connection, long reportId, UUID targetId,
                                String messageId, long capturedAt) throws SQLException {
        String json = redis.get("chat:recent:" + messageId);
        if (json == null) throw new SQLException("Le message de preuve a expiré");
        NetworkChatMessage message = NetworkChatMessage.fromJson(json);
        if (!targetId.equals(message.authorId())) throw new SQLException("Le message n'appartient pas à la cible");
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO tropicube_report_evidence
                    (report_id, message_id, author_uuid, body, context_json, instance_id,
                     sent_at, captured_at, content_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, reportId);
            statement.setString(2, message.messageId());
            statement.setString(3, message.authorId().toString());
            statement.setString(4, message.body());
            statement.setString(5, GSON.toJson(redis.recentList("chat:context:" + message.instanceId(), 7)));
            statement.setString(6, message.instanceId());
            statement.setLong(7, message.sentAt());
            statement.setLong(8, capturedAt);
            statement.setString(9, sha256(json));
            statement.executeUpdate();
        }
    }

    private List<ReportSummary> loadOpenReports(int limit) throws SQLException {
        List<ReportSummary> reports = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, reporter_uuid, target_uuid, category, status, assigned_staff_uuid, created_at
                     FROM tropicube_reports WHERE status IN ('OPEN', 'CLAIMED')
                     ORDER BY created_at LIMIT ?
                     """)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String staff = result.getString("assigned_staff_uuid");
                    reports.add(new ReportSummary(result.getLong("id"), UUID.fromString(result.getString("reporter_uuid")),
                            UUID.fromString(result.getString("target_uuid")), result.getString("category"),
                            result.getString("status"), staff == null ? null : UUID.fromString(staff),
                            result.getLong("created_at")));
                }
            }
        }
        return List.copyOf(reports);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record BanState(String playerName, String reason, long expiryEpochSecond) {}
}
