package fr.tropicube.core.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.SecuritySessionKeys;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Encrypted TOTP enrollment and network-connection-scoped secondary staff sessions. */
public final class StaffSecurityService {
    public static final int ENROLLMENT_SECONDS = 10 * 60;
    private static final Gson GSON = new Gson();
    private final DatabaseManager database;
    private final RedisManager redis;
    private final byte[] masterKey;

    public record Enrollment(String secret, List<String> recoveryCodes) {}

    public StaffSecurityService(DatabaseManager database, RedisManager redis, String encodedMasterKey) {
        this.database = database;
        this.redis = redis;
        if (encodedMasterKey == null || encodedMasterKey.isBlank()) {
            masterKey = null;
        } else {
            masterKey = Base64.getDecoder().decode(encodedMasterKey);
            if (masterKey.length != 32) throw new IllegalArgumentException(
                    "TROPICUBE_TOTP_MASTER_KEY doit contenir une clé Base64 de 32 octets");
        }
    }

    public boolean available() { return masterKey != null; }
    public boolean hasSession(UUID playerId) { return redis.exists(SecuritySessionKeys.staff(playerId)); }

    public String issueEnrollment(UUID playerId) {
        requireAvailable();
        String token = randomToken(18);
        redis.set("staff-enrollment-token:" + token, playerId.toString(), ENROLLMENT_SECONDS);
        return token;
    }

    public Enrollment beginEnrollment(UUID playerId, String token) {
        requireAvailable();
        String owner = redis.get("staff-enrollment-token:" + token);
        if (!playerId.toString().equals(owner)) throw new IllegalArgumentException("Jeton invalide ou expiré");
        String secret = Totp.newSecret();
        List<String> recovery = new ArrayList<>();
        for (int index = 0; index < 8; index++) recovery.add(randomToken(6));
        redis.set("staff-enrollment:" + playerId,
                GSON.toJson(new Enrollment(secret, recovery)), ENROLLMENT_SECONDS);
        redis.delete("staff-enrollment-token:" + token);
        return new Enrollment(secret, List.copyOf(recovery));
    }

    public CompletableFuture<Boolean> confirm(UUID playerId, String code) {
        String raw = redis.get("staff-enrollment:" + playerId);
        if (raw == null) return CompletableFuture.completedFuture(false);
        Enrollment enrollment = GSON.fromJson(raw, Enrollment.class);
        long now = System.currentTimeMillis() / 1000;
        long step = Totp.acceptedStep(enrollment.secret(), code, now, -1);
        if (step < 0) return CompletableFuture.completedFuture(false);
        return database.supplyAsync(() -> {
            List<String> hashes = enrollment.recoveryCodes().stream().map(StaffSecurityService::hash).toList();
            database.executeUpdate("""
                    INSERT INTO tropicube_staff_totp
                        (player_uuid, encrypted_secret, recovery_hashes, enabled_at, last_used_step)
                    VALUES (?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE encrypted_secret = VALUES(encrypted_secret),
                        recovery_hashes = VALUES(recovery_hashes), enabled_at = VALUES(enabled_at),
                        last_used_step = VALUES(last_used_step)
                    """, playerId.toString(), encrypt(enrollment.secret()), GSON.toJson(hashes),
                    System.currentTimeMillis(), step);
            redis.delete("staff-enrollment:" + playerId);
            openSession(playerId);
            return true;
        });
    }

    public CompletableFuture<Boolean> verify(UUID playerId, String code) {
        requireAvailable();
        return database.supplyAsync(() -> verifyStored(playerId, code));
    }

    /** Checks whether the player has completed enrollment without decrypting the stored secret. */
    public CompletableFuture<Boolean> isEnrolled(UUID playerId) {
        requireAvailable();
        return database.supplyAsync(() -> {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT 1 FROM tropicube_staff_totp WHERE player_uuid = ? LIMIT 1")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) { return result.next(); }
            }
        });
    }

    private boolean verifyStored(UUID playerId, String code) throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean verified = verifyAndConsume(connection, playerId, code);
                connection.commit();
                if (verified) openSession(playerId);
                return verified;
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    /** Serializes code consumption so one TOTP step or recovery code cannot win twice. */
    private boolean verifyAndConsume(Connection connection, UUID playerId, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT encrypted_secret, recovery_hashes, last_used_step FROM tropicube_staff_totp "
                        + "WHERE player_uuid = ? FOR UPDATE")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return false;
                String secret = decrypt(result.getString("encrypted_secret"));
                long lastStep = result.getLong("last_used_step");
                long step = Totp.acceptedStep(secret, code, System.currentTimeMillis() / 1000, lastStep);
                if (step >= 0) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE tropicube_staff_totp SET last_used_step = ? WHERE player_uuid = ?")) {
                        update.setLong(1, step);
                        update.setString(2, playerId.toString());
                        update.executeUpdate();
                    }
                    return true;
                }
                List<String> hashes = new ArrayList<>(GSON.fromJson(result.getString("recovery_hashes"),
                        new TypeToken<List<String>>() {}.getType()));
                if (!hashes.remove(hash(code))) return false;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE tropicube_staff_totp SET recovery_hashes = ? WHERE player_uuid = ?")) {
                    update.setString(1, GSON.toJson(hashes));
                    update.setString(2, playerId.toString());
                    update.executeUpdate();
                }
                return true;
            }
        }
    }

    private void openSession(UUID playerId) {
        redis.setPersistent(SecuritySessionKeys.staff(playerId), "verified");
    }

    private String encrypt(String value) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException error) { throw new IllegalStateException("Chiffrement TOTP impossible", error); }
    }

    private String decrypt(String value) {
        try {
            byte[] input = Base64.getDecoder().decode(value);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(128, input, 0, 12));
            return new String(cipher.doFinal(input, 12, input.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException error) { throw new IllegalStateException("Déchiffrement TOTP impossible", error); }
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException error) { throw new IllegalStateException(error); }
    }

    private static String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private void requireAvailable() {
        if (!available()) throw new IllegalStateException("La clé maîtresse TOTP n'est pas configurée");
    }
}
