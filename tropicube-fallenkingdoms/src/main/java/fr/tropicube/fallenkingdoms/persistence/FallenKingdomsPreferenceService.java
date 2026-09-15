package fr.tropicube.fallenkingdoms.persistence;

import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.fallenkingdoms.game.KingdomId;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Asynchronous per-game preferences; runtime callbacks must return to the Paper scheduler before touching players. */
public final class FallenKingdomsPreferenceService {
    private final DatabaseManager database;
    public FallenKingdomsPreferenceService(DatabaseManager database) { this.database = database; }

    public CompletableFuture<Preference> load(UUID playerId) {
        return database.supplyAsync(() -> {
            try (var connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT kit_id, kingdom_id FROM tropicube_fallenkingdoms_preferences WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) return new Preference(null, null);
                    String kingdom = rows.getString("kingdom_id");
                    return new Preference(rows.getString("kit_id"), kingdom == null ? null : KingdomId.valueOf(kingdom));
                }
            }
        });
    }

    public CompletableFuture<Void> save(UUID playerId, Preference preference) {
        return database.runAsync(() -> {
            try (var connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO tropicube_fallenkingdoms_preferences(player_uuid, kit_id, kingdom_id)
                         VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE kit_id=VALUES(kit_id),
                         kingdom_id=VALUES(kingdom_id), updated_at=CURRENT_TIMESTAMP
                         """)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, preference.kitId());
                statement.setString(3, preference.kingdom() == null ? null : preference.kingdom().name());
                statement.executeUpdate();
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException("Échec de persistance des préférences Fallen Kingdoms", failure);
            }
        });
    }

    public record Preference(String kitId, KingdomId kingdom) { }
}
