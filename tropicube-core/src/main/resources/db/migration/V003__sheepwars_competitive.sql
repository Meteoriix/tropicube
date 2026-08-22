CREATE TABLE IF NOT EXISTS tropicube_sheepwars_preferences (
    player_uuid VARCHAR(36) PRIMARY KEY,
    summary_visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_sheepwars_abandons (
    player_uuid VARCHAR(36) PRIMARY KEY,
    recent_strikes SMALLINT NOT NULL DEFAULT 0,
    last_abandon_at BIGINT NOT NULL,
    penalty_until BIGINT NOT NULL,
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE,
    INDEX idx_sheepwars_penalty (penalty_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
