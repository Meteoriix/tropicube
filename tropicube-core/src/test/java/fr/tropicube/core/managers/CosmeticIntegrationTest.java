package fr.tropicube.core.managers;

import fr.tropicube.core.cosmetic.CosmeticCatalog;
import fr.tropicube.core.cosmetic.CosmeticService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.UUID;
import static fr.tropicube.core.cosmetic.CosmeticCatalog.*;
import static fr.tropicube.core.cosmetic.CosmeticService.Result.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real SQL on the disposable infrastructure only; each test owns a separate synthetic account. */
@EnabledIfEnvironmentVariable(named = "TROPICUBE_TEST_MYSQL_URL", matches = ".+")
class CosmeticIntegrationTest {
    private UUID player;
    private static CosmeticCatalog catalog;

    private static Connection connection() throws SQLException {
        String url = System.getenv("TROPICUBE_TEST_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://127[.]0[.]0[.]1:[0-9]+/tropicube_integration(?:[?].*)?"), "Refuse a non-disposable database");
        return DriverManager.getConnection(url, "tropicube_test", "integration-only");
    }

    @BeforeAll static void migrate() throws Exception {
        try (var input = CosmeticIntegrationTest.class.getResourceAsStream("/cosmetics.yml")) { catalog = CosmeticCatalog.load(input); }
        try (var c = connection()) {
            for (String sql : DatabaseSchema.baseStatements()) try (var s = c.createStatement()) { s.execute(sql); }
            UUID existing = UUID.randomUUID();
            createAccount(c, existing);
            try {
                new SchemaMigrationManager(message -> { }).migrate(c);
                new SchemaMigrationManager(message -> { }).migrate(c);
                var snapshot = CosmeticService.consult(c, existing);
                assertEquals(1000, snapshot.balance().intValueExact());
                assertTrue(snapshot.equipped().isEmpty(), "Migration must not equip anything implicitly");
                assertTrue(snapshot.purchased().isEmpty());
            } finally { execute(c, "DELETE FROM tropicube_players WHERE uuid=?", existing.toString()); }
        }
    }
    @BeforeEach void account() throws Exception {
        player = UUID.randomUUID();
        try (var c = connection()) { createAccount(c, player); }
    }
    @AfterEach void cleanup() throws Exception {
        try (var c = connection()) {
            execute(c, "DELETE FROM tropicube_transactions WHERE from_uuid=? OR to_uuid=?", player.toString(), player.toString());
            execute(c, "DELETE FROM tropicube_players WHERE uuid=?", player.toString());
        }
    }
    @Test void equipmentSurvivesReconnectionAndCategoriesRemainIndependent() throws Exception {
        try (var c = connection()) {
            assertEquals(SUCCESS, CosmeticService.equip(c, player, Category.TRAIL, catalog.find("breeze")));
            assertEquals(SUCCESS, CosmeticService.equip(c, player, Category.SOUND, catalog.find("chime")));
            assertEquals(SUCCESS, CosmeticService.equip(c, player, Category.TRAIL, catalog.find("breeze")));
        }
        try (var reconnected = connection()) {
            var snapshot = CosmeticService.consult(reconnected, player);
            assertEquals(2, snapshot.equipped().size());
            assertEquals("breeze", snapshot.equipped().get(Category.TRAIL));
            assertEquals("chime", snapshot.equipped().get(Category.SOUND));
            assertEquals(SUCCESS, CosmeticService.equip(reconnected, player, Category.TRAIL, null));
            assertEquals(SUCCESS, CosmeticService.equip(reconnected, player, Category.TRAIL, null));
            assertEquals(1, CosmeticService.consult(reconnected, player).equipped().size());
        }
    }
    @Test void revalidatesLevelVipAndCategoryAndRetainsSuspendedSelection() throws Exception {
        try (var c = connection()) {
            assertEquals(LOCKED, CosmeticService.equip(c, player, Category.TRAIL, catalog.find("sparks")));
            assertEquals(LOCKED, CosmeticService.equip(c, player, Category.TRAIL, catalog.find("fireflies")));
            assertEquals(INVALID_SELECTION, CosmeticService.equip(c, player, Category.SOUND, catalog.find("breeze")));
            execute(c, "UPDATE tropicube_players SET vip_level=1 WHERE uuid=?", player.toString());
            assertEquals(SUCCESS, CosmeticService.equip(c, player, Category.TRAIL, catalog.find("hearts")));
            execute(c, "UPDATE tropicube_players SET vip_level=0 WHERE uuid=?", player.toString());
            var snapshot = CosmeticService.consult(c, player);
            assertEquals("hearts", snapshot.equipped().get(Category.TRAIL));
            assertFalse(snapshot.available(catalog.find("hearts")));
            assertEquals(LOCKED, CosmeticService.equip(c, player, Category.TRAIL, catalog.find("hearts")));
            assertEquals(SUCCESS, CosmeticService.equip(c, player, Category.TRAIL, null));
            assertEquals(ACCOUNT_NOT_FOUND, CosmeticService.equip(c, UUID.randomUUID(), Category.TRAIL, catalog.find("breeze")));
        }
    }
    @Test void anonymizationCascadeRemovesEquipment() throws Exception {
        try (var c = connection()) {
            assertEquals(SUCCESS, CosmeticService.equip(c, player, Category.TRAIL, catalog.find("breeze")));
            execute(c, "DELETE FROM tropicube_players WHERE uuid=?", player.toString());
            try (var s = c.prepareStatement("SELECT COUNT(*) FROM tropicube_cosmetic_equipment WHERE player_uuid=?")) {
                s.setString(1, player.toString());
                try (var rows = s.executeQuery()) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
            }
        }
    }
    private static void createAccount(Connection c, UUID id) throws SQLException {
        execute(c, "INSERT INTO tropicube_players(uuid,username,first_join,last_join) VALUES(?,?,0,0)", id.toString(), "CosmeticTest");
        execute(c, "INSERT INTO tropicube_economy(uuid,balance,last_updated) VALUES(?,1000,0)", id.toString());
    }
    private static void execute(Connection c, String sql, Object... values) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) s.setObject(index + 1, values[index]);
            s.executeUpdate();
        }
    }
}
