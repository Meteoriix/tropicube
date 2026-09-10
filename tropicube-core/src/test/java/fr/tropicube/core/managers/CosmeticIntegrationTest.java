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
    @Test void concurrentRepeatedPurchasesDebitExactlyOnceAndSurviveReconnection() throws Exception {
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var calls = new java.util.ArrayList<java.util.concurrent.Future<CosmeticService.Result>>();
            for (int i = 0; i < 8; i++) calls.add(pool.submit(() -> {
                start.await();
                try (var c = connection()) { return CosmeticService.purchase(c, player, catalog.find("fireflies"), 500); }
            }));
            start.countDown();
            var outcomes = new java.util.ArrayList<CosmeticService.Result>();
            for (var call : calls) outcomes.add(call.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(result -> result == SUCCESS).count());
            assertEquals(7, outcomes.stream().filter(result -> result == OWNED).count());
        }
        try (var c = connection()) {
            var snapshot = CosmeticService.consult(c, player);
            assertEquals(500, snapshot.balance().intValueExact());
            assertEquals(java.util.Set.of("fireflies"), snapshot.purchased());
            assertTrue(snapshot.equipped().isEmpty(), "A purchase must never equip automatically");
            assertEquals(500, scalar(c, "SELECT total_spent FROM tropicube_economy WHERE uuid=?"));
            assertEquals(1, scalar(c, "SELECT COUNT(*) FROM tropicube_transactions WHERE from_uuid=?"));
            assertEquals(SUCCESS, CosmeticService.equip(c, player, Category.TRAIL, catalog.find("fireflies")));
        }
        try (var c = connection()) {
            assertEquals("fireflies", CosmeticService.consult(c, player).equipped().get(Category.TRAIL));
        }
    }
    @Test void concurrentDifferentPurchasesCannotOverdrawTheAccount() throws Exception {
        try (var c = connection()) { execute(c, "UPDATE tropicube_economy SET balance=600 WHERE uuid=?", player.toString()); }
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var calls = new java.util.ArrayList<java.util.concurrent.Future<CosmeticService.Result>>();
            for (String id : java.util.List.of("fireflies", "crystal")) calls.add(pool.submit(() -> {
                start.await();
                try (var c = connection()) { return CosmeticService.purchase(c, player, catalog.find(id), catalog.find(id).requirement()); }
            }));
            start.countDown();
            var results = java.util.Set.of(calls.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS), calls.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(java.util.Set.of(SUCCESS, INSUFFICIENT_FUNDS), results);
        }
        try (var c = connection()) {
            assertTrue(CosmeticService.consult(c, player).balance().signum() >= 0);
            assertEquals(1, scalar(c, "SELECT COUNT(*) FROM tropicube_cosmetic_purchases WHERE player_uuid=?"));
        }
    }
    @Test void insufficientFundsChangedPriceAndInvalidAccessNeverDebit() throws Exception {
        try (var c = connection()) {
            assertEquals(PRICE_CHANGED, CosmeticService.purchase(c, player, catalog.find("fireflies"), 499));
            assertEquals(LOCKED, CosmeticService.purchase(c, player, catalog.find("breeze"), 0));
            execute(c, "UPDATE tropicube_economy SET balance=299.99 WHERE uuid=?", player.toString());
            assertEquals(INSUFFICIENT_FUNDS, CosmeticService.purchase(c, player, catalog.find("crystal"), 300));
            assertEquals(new java.math.BigDecimal("299.99"), CosmeticService.consult(c, player).balance());
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM tropicube_transactions WHERE from_uuid=?"));
            assertEquals(ACCOUNT_NOT_FOUND, CosmeticService.purchase(c, UUID.randomUUID(), catalog.find("crystal"), 300));
        }
    }
    @Test void journalFailureRollsBackBothDebitAndOwnershipAndAllowsRetry() throws Exception {
        try (var c = connection()) {
            Connection failing = (Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                && sql.startsWith("INSERT INTO tropicube_transactions")) throw new SQLException("Injected journal failure");
                        try { return method.invoke(c, args); }
                        catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                    });
            assertThrows(SQLException.class, () -> CosmeticService.purchase(failing, player, catalog.find("fireflies"), 500));
            assertTrue(c.getAutoCommit());
            var snapshot = CosmeticService.consult(c, player);
            assertEquals(1000, snapshot.balance().intValueExact());
            assertTrue(snapshot.purchased().isEmpty());
            assertEquals(0, scalar(c, "SELECT total_spent FROM tropicube_economy WHERE uuid=?"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM tropicube_transactions WHERE from_uuid=?"));
            assertEquals(SUCCESS, CosmeticService.purchase(c, player, catalog.find("fireflies"), 500));
        }
    }
    @Test void missionExperienceAndCurrencyUnlockExistingCatalogueRules() throws Exception {
        try (var c = connection()) {
            // This is the cumulative progression row written by MissionService, without any new reward mechanism.
            execute(c, "INSERT INTO tropicube_network_progression(player_uuid,experience,level,updated_at) VALUES(?,1600,5,0)", player.toString());
            assertEquals(SUCCESS, CosmeticService.equip(c, player, Category.TRAIL, catalog.find("sparks")));
            assertEquals(SUCCESS, CosmeticService.equip(c, player, Category.SOUND, catalog.find("xylophone")));
            assertEquals(SUCCESS, CosmeticService.purchase(c, player, catalog.find("crystal"), 300));
            execute(c, "DELETE FROM tropicube_players WHERE uuid=?", player.toString());
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM tropicube_cosmetic_purchases WHERE player_uuid=?"));
            assertEquals(0, scalar(c, "SELECT COUNT(*) FROM tropicube_cosmetic_equipment WHERE player_uuid=?"));
        }
    }
    private long scalar(Connection c, String sql) throws SQLException {
        try (var statement = c.prepareStatement(sql)) {
            statement.setString(1, player.toString());
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getLong(1); }
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
