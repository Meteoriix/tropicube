package fr.tropicube.core.clan;

import fr.tropicube.core.managers.DatabaseManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static fr.tropicube.core.clan.ClanService.Result.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL integration against an explicitly supplied, disposable loopback database only. */
@EnabledIfEnvironmentVariable(named = "TROPICUBE_CLAN_TEST_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClanSqlTest {
    private String url;
    private DatabaseManager database;

    @BeforeAll void schema() throws Exception {
        url = System.getenv("TROPICUBE_CLAN_TEST_URL");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/clan_test(?:\\?.*)?"), "Disposable database URL required");
        database = new DatabaseManager((key, fallback) -> fallback) {
            @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, "root", ""); }
        };
        try (Connection connection = database.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : List.of("tropicube_guild_members", "tropicube_guild_invites",
                    "tropicube_guild_audit", "tropicube_guild_challenges", "tropicube_guild_season_scores",
                    "tropicube_guilds", "tropicube_clan_members", "tropicube_clan_invites",
                    "tropicube_clan_audit", "tropicube_clan_challenges", "tropicube_clan_season_scores",
                    "tropicube_clans", "tropicube_notifications", "tropicube_players", "tropicube_seasons")) {
                sql.execute("DROP TABLE IF EXISTS " + table);
            }
            sql.execute("SET FOREIGN_KEY_CHECKS=1");
            sql.execute("CREATE TABLE IF NOT EXISTS tropicube_players(uuid VARCHAR(36) PRIMARY KEY, username VARCHAR(16) NOT NULL)");
            sql.execute("CREATE TABLE IF NOT EXISTS tropicube_seasons(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE tropicube_notifications(category VARCHAR(32), message_key VARCHAR(191), action_json TEXT)");
            String migration = Files.readString(Path.of("src/main/resources/db/migration/V001__network_features.sql"));
            var matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS tropicube_guild[a-z_]* \\(.*?;", Pattern.DOTALL).matcher(migration);
            while (matcher.find()) sql.execute(matcher.group());
            String progression = Files.readString(Path.of("src/main/resources/db/migration/V002__guild_progression.sql"));
            for (String statement : progression.split(";")) if (!statement.isBlank()) sql.execute(statement);
            sql.executeUpdate("INSERT INTO tropicube_notifications(category, message_key, action_json) "
                    + "VALUES ('GUILD', 'guild.invitation', '{\"command\":\"/guild accept TEST\"}')");
            String rename = Files.readString(Path.of("src/main/resources/db/migration/V013__rename_guilds_to_clans.sql"));
            for (String statement : rename.replaceAll("(?m)^--.*$", "").split(";"))
                if (!statement.isBlank()) sql.execute(statement);

            try (ResultSet notification = sql.executeQuery(
                    "SELECT category, message_key, action_json FROM tropicube_notifications")) {
                assertTrue(notification.next());
                assertEquals("CLAN", notification.getString("category"));
                assertEquals("clan.invitation", notification.getString("message_key"));
                assertEquals("{\"command\":\"/clan accept TEST\"}", notification.getString("action_json"));
            }
            try (ResultSet legacyTables = sql.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema=DATABASE() AND table_name LIKE 'tropicube_guild%'")) {
                assertTrue(legacyTables.next());
                assertEquals(0, legacyTables.getInt(1));
            }
        }
    }

    @AfterAll void shutdown() { if (database != null) database.close(); }

    @Test void expiredInvitationsAreHiddenAndCannotBeAccepted() throws Exception {
        var service = new ClanService(database, 5, 1, 5000);
        UUID owner = player(), member = player();
        String tag = tag();
        assertEquals(SUCCESS, await(service.create(owner, "Clan " + tag, tag)));
        assertEquals(SUCCESS, await(service.invite(owner, member)));
        assertEquals(1, await(service.invitations(member)).size());
        execute("UPDATE tropicube_clan_invites SET expires_at=0 WHERE player_uuid=?", member.toString());
        assertTrue(await(service.invitations(member)).isEmpty());
        assertEquals(NOT_FOUND, await(service.accept(member, tag)));
    }

    @Test void concurrentAcceptsNeverExceedCapacity() throws Exception {
        var service = new ClanService(database, 2, 1, 5000);
        UUID owner = player(), first = player(), second = player();
        String tag = tag();
        assertEquals(SUCCESS, await(service.create(owner, "Clan " + tag, tag)));
        assertEquals(SUCCESS, await(service.invite(owner, first)));
        assertEquals(SUCCESS, await(service.invite(owner, second)));
        var a = service.accept(first, tag);
        var b = service.accept(second, tag);
        assertEquals(java.util.Set.of(SUCCESS, FULL), java.util.Set.of(await(a), await(b)));
        assertEquals(2, await(service.clan(owner)).members().size());
    }

    @Test void rightsTransferAndStaleScreensAreCheckedAtMutationTime() throws Exception {
        var service = new ClanService(database, 5, 1, 5000);
        UUID owner = player(), member = player(), third = player();
        String tag = tag();
        assertEquals(SUCCESS, await(service.create(owner, "Clan " + tag, tag)));
        assertEquals(SUCCESS, await(service.invite(owner, member)));
        assertEquals(SUCCESS, await(service.accept(member, tag)));
        long id = await(service.clan(owner)).id();
        assertEquals(NOT_ALLOWED, await(service.invite(member, third)));
        assertEquals(NOT_ALLOWED, await(service.leave(owner)));
        assertEquals(SUCCESS, await(service.setRole(owner, member, ClanService.Role.OFFICER)));
        assertEquals(SUCCESS, await(service.setRole(owner, member, ClanService.Role.OFFICER)));
        assertEquals(NOT_ALLOWED, await(service.kick(member, owner)));
        assertEquals(SUCCESS, await(service.transfer(owner, member)));
        assertEquals(member, await(service.clan(member)).ownerId());
        assertEquals(NOT_ALLOWED, await(service.administer(owner, id, ClanService.Administration.PROMOTE, member)));
        assertEquals(SUCCESS, await(service.leave(owner)));
        String newTag = tag();
        assertEquals(SUCCESS, await(service.create(owner, "Clan " + newTag, newTag)));
        assertEquals(NOT_FOUND, await(service.administer(owner, id, ClanService.Administration.LEAVE, null)));
        assertNotNull(await(service.clan(owner)));
        assertEquals(SUCCESS, await(service.leave(member)));
        assertNull(await(service.clan(member)));
    }

    @Test void weeklyDisplayDoesNotShowLastWeeksContribution() throws Exception {
        var service = new ClanService(database, 5, 1, 5000);
        UUID owner = player();
        String tag = tag();
        assertEquals(SUCCESS, await(service.create(owner, "Clan " + tag, tag)));
        execute("UPDATE tropicube_clan_members SET weekly_contribution=5000, contribution_week='2000-W01' WHERE player_uuid=?", owner.toString());
        assertEquals(0, await(service.clan(owner)).members().getFirst().weeklyContribution());
    }

    private UUID player() throws SQLException {
        UUID id = UUID.randomUUID();
        execute("INSERT INTO tropicube_players(uuid, username) VALUES (?, ?)", id.toString(), "P" + tag());
        return id;
    }
    private static String tag() { return UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT); }
    private void execute(String sql, Object... arguments) throws SQLException {
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) statement.setObject(i + 1, arguments[i]);
            statement.executeUpdate();
        }
    }
    private static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(15, TimeUnit.SECONDS); }
}
