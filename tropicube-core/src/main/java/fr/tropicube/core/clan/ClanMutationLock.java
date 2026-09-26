package fr.tropicube.core.clan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Serializes rare clan administration across instances, including checks before writes.
 * MySQL advisory locks survive transactions and must be released before returning a pooled connection.
 * Gameplay contributions do not acquire this lock. Call only on a database worker.
 */
final class ClanMutationLock implements AutoCloseable {
    private final Connection connection;
    private final String name;

    private ClanMutationLock(Connection connection, String name) {
        this.connection = connection;
        this.name = name;
    }

    static ClanMutationLock acquire(Connection connection) throws SQLException {
        String name = "tc:clan:" + java.util.UUID.nameUUIDFromBytes(connection.getCatalog().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 5)")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) throw new SQLException("Clan administration lock timed out");
            }
        }
        try {
            connection.setAutoCommit(false);
            return new ClanMutationLock(connection, name);
        } catch (SQLException failure) {
            connection.abort(Runnable::run);
            throw failure;
        }
    }

    @Override
    public void close() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) throw new SQLException("Clan lock release failed");
            }
        } catch (SQLException failure) {
            // Never return a session holding an advisory lock to the pool.
            connection.abort(Runnable::run);
            throw failure;
        }
    }
}
