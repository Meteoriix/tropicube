package fr.tropicube.core.managers;

/** Legacy base tables needed before ordered migrations, shared by startup and real database tests. */
final class DatabaseSchema {
    private DatabaseSchema() { }
    static String[] baseStatements() {
        return new String[] {
            // Players table
            """
            CREATE TABLE IF NOT EXISTS tropicube_players (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                display_name VARCHAR(64),
                first_join BIGINT NOT NULL,
                last_join BIGINT NOT NULL,
                play_time BIGINT DEFAULT 0,
                language VARCHAR(8) DEFAULT 'fr',
                grade VARCHAR(32) DEFAULT 'JOUEUR',
                grade_expiry BIGINT DEFAULT -1,
                vip_level SMALLINT NOT NULL DEFAULT 0,
                mod_level SMALLINT NOT NULL DEFAULT 0,
                access_revision BIGINT NOT NULL DEFAULT 0,
                is_banned BOOLEAN DEFAULT FALSE,
                ban_reason TEXT,
                ban_expiry BIGINT DEFAULT 0,
                INDEX idx_username (username)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Table monnaie
            """
            CREATE TABLE IF NOT EXISTS tropicube_economy (
                uuid VARCHAR(36) PRIMARY KEY,
                balance DECIMAL(19,2) DEFAULT 0.00,
                total_earned DECIMAL(19,2) DEFAULT 0.00,
                total_spent DECIMAL(19,2) DEFAULT 0.00,
                last_updated BIGINT NOT NULL,
                FOREIGN KEY (uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Table transactions
            """
            CREATE TABLE IF NOT EXISTS tropicube_transactions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                from_uuid VARCHAR(36),
                to_uuid VARCHAR(36),
                amount DECIMAL(19,2) NOT NULL,
                reason VARCHAR(255),
                transaction_type VARCHAR(32) NOT NULL,
                timestamp BIGINT NOT NULL,
                INDEX idx_from (from_uuid),
                INDEX idx_to (to_uuid),
                INDEX idx_timestamp (timestamp)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Table sanctions (mutes, warns, kicks)
            """
            CREATE TABLE IF NOT EXISTS tropicube_sanctions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                type VARCHAR(16) NOT NULL,
                reason TEXT,
                staff_uuid VARCHAR(36),
                staff_name VARCHAR(16),
                timestamp BIGINT NOT NULL,
                expiry BIGINT DEFAULT -1,
                active BOOLEAN DEFAULT TRUE,
                INDEX idx_player (player_uuid),
                INDEX idx_type (type),
                INDEX idx_active (active)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Table grades
            """
            CREATE TABLE IF NOT EXISTS tropicube_grades (
                name VARCHAR(32) PRIMARY KEY,
                display_name VARCHAR(64) NOT NULL,
                prefix VARCHAR(64) DEFAULT '',
                suffix VARCHAR(64) DEFAULT '',
                color VARCHAR(32) DEFAULT '<white>',
                priority INT DEFAULT 0,
                default_vip_level SMALLINT NOT NULL DEFAULT 0,
                default_mod_level SMALLINT NOT NULL DEFAULT 0
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            """
            CREATE TABLE IF NOT EXISTS tropicube_sheepwars (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                playerKit VARCHAR(16) NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,

            // Symmetric friendship relation and pending requests.
            """
            CREATE TABLE IF NOT EXISTS tropicube_friendships (
                player_a VARCHAR(36) NOT NULL,
                player_b VARCHAR(36) NOT NULL,
                requester_uuid VARCHAR(36) NOT NULL,
                status VARCHAR(16) NOT NULL,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL,
                PRIMARY KEY (player_a, player_b),
                INDEX idx_friend_requester (requester_uuid, status),
                INDEX idx_friend_status_a (player_a, status),
                INDEX idx_friend_status_b (player_b, status),
                FOREIGN KEY (player_a) REFERENCES tropicube_players(uuid) ON DELETE CASCADE,
                FOREIGN KEY (player_b) REFERENCES tropicube_players(uuid) ON DELETE CASCADE,
                FOREIGN KEY (requester_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
        };
    }
}
