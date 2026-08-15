package fr.tropicube.core.social;

import fr.tropicube.core.managers.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** MySQL repository enforcing the undirected friendship relation. */
public final class FriendshipRepository {

    public enum RequestResult { CREATED, ALREADY_FRIENDS, ALREADY_PENDING, LIMIT_REACHED }
    public record FriendView(UUID playerId, String username, long lastJoin) { }
    public record PendingRequest(UUID requesterId, String username, long createdAt) { }

    private final DatabaseManager database;

    public FriendshipRepository(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public RequestResult request(UUID requester, UUID target, int maximumFriends) throws SQLException {
        Pair pair = Pair.of(requester, target);
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (countAccepted(connection, requester) >= maximumFriends
                        || countAccepted(connection, target) >= maximumFriends) {
                    connection.rollback();
                    return RequestResult.LIMIT_REACHED;
                }
                try (PreparedStatement existing = connection.prepareStatement(
                        "SELECT status FROM tropicube_friendships WHERE player_a = ? AND player_b = ? FOR UPDATE")) {
                    existing.setString(1, pair.first().toString());
                    existing.setString(2, pair.second().toString());
                    try (ResultSet result = existing.executeQuery()) {
                        if (result.next()) {
                            connection.rollback();
                            return "ACCEPTED".equals(result.getString(1))
                                    ? RequestResult.ALREADY_FRIENDS : RequestResult.ALREADY_PENDING;
                        }
                    }
                }
                long now = System.currentTimeMillis() / 1000;
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO tropicube_friendships
                            (player_a, player_b, requester_uuid, status, created_at, updated_at)
                        VALUES (?, ?, ?, 'PENDING', ?, ?)
                        """)) {
                    insert.setString(1, pair.first().toString());
                    insert.setString(2, pair.second().toString());
                    insert.setString(3, requester.toString());
                    insert.setLong(4, now);
                    insert.setLong(5, now);
                    insert.executeUpdate();
                }
                connection.commit();
                return RequestResult.CREATED;
            } catch (SQLIntegrityConstraintViolationException exception) {
                connection.rollback();
                return RequestResult.ALREADY_PENDING;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean accept(UUID target, UUID requester) throws SQLException {
        Pair pair = Pair.of(target, requester);
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE tropicube_friendships SET status = 'ACCEPTED', updated_at = ?
                     WHERE player_a = ? AND player_b = ? AND requester_uuid = ? AND status = 'PENDING'
                     """)) {
            statement.setLong(1, System.currentTimeMillis() / 1000);
            statement.setString(2, pair.first().toString());
            statement.setString(3, pair.second().toString());
            statement.setString(4, requester.toString());
            return statement.executeUpdate() == 1;
        }
    }

    public boolean deny(UUID target, UUID requester) throws SQLException {
        Pair pair = Pair.of(target, requester);
        return delete(pair, "requester_uuid = ? AND status = 'PENDING'", requester);
    }

    public boolean remove(UUID player, UUID friend) throws SQLException {
        return delete(Pair.of(player, friend), "status = 'ACCEPTED'", null);
    }

    private boolean delete(Pair pair, String condition, UUID requester) throws SQLException {
        String sql = "DELETE FROM tropicube_friendships WHERE player_a = ? AND player_b = ? AND " + condition;
        try (Connection connection = database.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pair.first().toString());
            statement.setString(2, pair.second().toString());
            if (requester != null) statement.setString(3, requester.toString());
            return statement.executeUpdate() == 1;
        }
    }

    public boolean areFriends(UUID first, UUID second) throws SQLException {
        Pair pair = Pair.of(first, second);
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM tropicube_friendships WHERE player_a = ? AND player_b = ? AND status = 'ACCEPTED'")) {
            statement.setString(1, pair.first().toString());
            statement.setString(2, pair.second().toString());
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    public List<FriendView> friends(UUID player) throws SQLException {
        List<FriendView> friends = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.uuid, p.username, p.last_join
                     FROM tropicube_friendships f
                     JOIN tropicube_players p ON p.uuid = IF(f.player_a = ?, f.player_b, f.player_a)
                     WHERE (f.player_a = ? OR f.player_b = ?) AND f.status = 'ACCEPTED'
                     ORDER BY LOWER(p.username)
                     """)) {
            String value = player.toString();
            statement.setString(1, value);
            statement.setString(2, value);
            statement.setString(3, value);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) friends.add(new FriendView(UUID.fromString(result.getString(1)),
                        result.getString(2), result.getLong(3)));
            }
        }
        return List.copyOf(friends);
    }

    public List<PendingRequest> requests(UUID target) throws SQLException {
        List<PendingRequest> requests = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.uuid, p.username, f.created_at
                     FROM tropicube_friendships f JOIN tropicube_players p ON p.uuid = f.requester_uuid
                     WHERE (f.player_a = ? OR f.player_b = ?) AND f.requester_uuid <> ? AND f.status = 'PENDING'
                     ORDER BY f.created_at DESC
                     """)) {
            String value = target.toString();
            statement.setString(1, value);
            statement.setString(2, value);
            statement.setString(3, value);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) requests.add(new PendingRequest(UUID.fromString(result.getString(1)),
                        result.getString(2), result.getLong(3)));
            }
        }
        return List.copyOf(requests);
    }

    public void expireRequests(int expiryDays) throws SQLException {
        long threshold = System.currentTimeMillis() / 1000 - expiryDays * 86_400L;
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM tropicube_friendships WHERE status = 'PENDING' AND created_at < ?")) {
            statement.setLong(1, threshold);
            statement.executeUpdate();
        }
    }

    private static int countAccepted(Connection connection, UUID player) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tropicube_friendships WHERE (player_a = ? OR player_b = ?) AND status = 'ACCEPTED'")) {
            statement.setString(1, player.toString());
            statement.setString(2, player.toString());
            try (ResultSet result = statement.executeQuery()) { result.next(); return result.getInt(1); }
        }
    }

    /** Canonical UUID order used by the database primary key. */
    public record Pair(UUID first, UUID second) {
        public Pair {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            if (first.equals(second)) throw new IllegalArgumentException("Une relation sociale exige deux joueurs distincts");
        }

        public static Pair of(UUID first, UUID second) {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            return first.toString().compareTo(second.toString()) < 0
                    ? new Pair(first, second) : new Pair(second, first);
        }
    }
}
