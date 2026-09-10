CREATE TABLE IF NOT EXISTS tropicube_cosmetic_purchases (
    player_uuid VARCHAR(36) NOT NULL,
    cosmetic_id VARCHAR(48) NOT NULL,
    price DECIMAL(18,2) NOT NULL,
    acquired_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, cosmetic_id),
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_cosmetic_equipment (
    player_uuid VARCHAR(36) NOT NULL,
    category VARCHAR(16) NOT NULL,
    cosmetic_id VARCHAR(48) NOT NULL,
    PRIMARY KEY (player_uuid, category),
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
