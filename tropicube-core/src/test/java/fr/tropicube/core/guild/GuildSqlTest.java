package fr.tropicube.core.guild;

import fr.tropicube.core.managers.DatabaseManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static fr.tropicube.core.guild.GuildService.Result.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL integration against an explicitly supplied, disposable loopback database only. */
@EnabledIfEnvironmentVariable(named = "TROPICUBE_GUILD_TEST_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GuildSqlTest {
    private String url;
    private DatabaseManager database;

    @BeforeAll void schema() throws Exception {
        url = System.getenv("TROPICUBE_GUILD_TEST_URL");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/guild_test(?:\\?.*)?"), "Disposable database URL required");
        database = new DatabaseManager(null) {
            @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, "root", ""); }
        };
        try (Connection connection = database.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE IF NOT EXISTS tropicube_players(uuid VARCHAR(36) PRIMARY KEY, username VARCHAR(16) NOT NULL)");
            sql.execute("CREATE TABLE IF NOT EXISTS tropicube_seasons(id BIGINT PRIMARY KEY)");
            String migration = Files.readString(Path.of("src/main/resources/db/migration/V001__network_features.sql"));
            var matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS tropicube_guild[a-z_]* \\(.*?;", Pattern.DOTALL).matcher(migration);
            while (matcher.find()) sql.execute(matcher.group());
            String progression = Files.readString(Path.of("src/main/resources/db/migration/V002__guild_progression.sql"));
            for (String statement : progression.split(";")) if (!statement.isBlank()) sql.execute(statement);
        }
    }

    @AfterAll void shutdown() { if (database != null) database.close(); }

    @Test void expiredInvitationsAreHiddenAndCannotBeAccepted() throws Exception {
        var service = new GuildService(database, 5, 1, 5000);
        UUID owner = player(), member = player();
        String tag = tag();
        assertEquals(SUCCESS, await(service.create(owner, "Guild " + tag, tag)));
        assertEquals(SUCCESS, await(service.invite(owner, member)));
        assertEquals(1, await(service.invitations(member)).size());
        execute("UPDATE tropicube_guild_invites SET expires_at=0 WHERE player_uuid=?", member.toString());
        assertTrue(await(service.invitations(member)).isEmpty());
        assertEquals(NOT_FOUND, await(service.accept(member, tag)));
    }

    @Test void concurrentAcceptsNeverExceedCapacity() throws Exception {
        var service = new GuildService(database, 2, 1, 5000);
        UUID owner = player(), first = player(), second = player();
        String tag = tag();
        assertEquals(SUCCESS, await(service.create(owner, "Guild " + tag, tag)));
        assertEquals(SUCCESS, await(service.invite(owner, first)));
        assertEquals(SUCCESS, await(service.invite(owner, second)));
        var a = service.accept(first, tag);
        var b = service.accept(second, tag);
        assertEquals(java.util.Set.of(SUCCESS, FULL), java.util.Set.of(await(a), await(b)));
        assertEquals(2, await(service.guild(owner)).members().size());
    }

    @Test void rightsTransferAndStaleScreensAreCheckedAtMutationTime() throws Exception {
        var service = new GuildService(database, 5, 1, 5000);
        UUID owner = player(), member = player(), third = player();
        String tag = tag();
        assertEquals(SUCCESS, await(service.create(owner, "Guild " + tag, tag)));
        assertEquals(SUCCESS, await(service.invite(owner, member)));
        assertEquals(SUCCESS, await(service.accept(member, tag)));
        long id = await(service.guild(owner)).id();
        assertEquals(NOT_ALLOWED, await(service.invite(member, third)));
        assertEquals(NOT_ALLOWED, await(service.leave(owner)));
        assertEquals(SUCCESS, await(service.setRole(owner, member, GuildService.Role.OFFICER)));
        assertEquals(SUCCESS, await(service.setRole(owner, member, GuildService.Role.OFFICER)));
        assertEquals(NOT_ALLOWED, await(service.kick(member, owner)));
        assertEquals(SUCCESS, await(service.transfer(owner, member)));
        assertEquals(member, await(service.guild(member)).ownerId());
        assertEquals(NOT_ALLOWED, await(service.administer(owner, id, GuildService.Administration.PROMOTE, member)));
        assertEquals(SUCCESS, await(service.leave(owner)));
        String newTag = tag();
        assertEquals(SUCCESS, await(service.create(owner, "Guild " + newTag, newTag)));
        assertEquals(NOT_FOUND, await(service.administer(owner, id, GuildService.Administration.LEAVE, null)));
        assertNotNull(await(service.guild(owner)));
        assertEquals(SUCCESS, await(service.leave(member)));
        assertNull(await(service.guild(member)));
    }

    @Test void weeklyDisplayDoesNotShowLastWeeksContribution() throws Exception {
        var service = new GuildService(database, 5, 1, 5000);
        UUID owner = player();
        String tag = tag();
        assertEquals(SUCCESS, await(service.create(owner, "Guild " + tag, tag)));
        execute("UPDATE tropicube_guild_members SET weekly_contribution=5000, contribution_week='2000-W01' WHERE player_uuid=?", owner.toString());
        assertEquals(0, await(service.guild(owner)).members().getFirst().weeklyContribution());
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
