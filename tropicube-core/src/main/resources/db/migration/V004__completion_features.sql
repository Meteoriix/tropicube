SET @tropicube_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'tropicube_sheepwars_kit_mastery'
      AND column_name = 'selected_catalog_version'
);
SET @tropicube_ddl = IF(
    @tropicube_column_exists = 0,
    'ALTER TABLE tropicube_sheepwars_kit_mastery ADD COLUMN selected_catalog_version INT AFTER selected_branch',
    'SELECT 1'
);
PREPARE tropicube_migration_statement FROM @tropicube_ddl;
EXECUTE tropicube_migration_statement;
DEALLOCATE PREPARE tropicube_migration_statement;

SET @tropicube_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'tropicube_sheepwars_matches'
      AND column_name = 'mastery_catalog_version'
);
SET @tropicube_ddl = IF(
    @tropicube_column_exists = 0,
    'ALTER TABLE tropicube_sheepwars_matches ADD COLUMN mastery_catalog_version INT AFTER timeline_json',
    'SELECT 1'
);
PREPARE tropicube_migration_statement FROM @tropicube_ddl;
EXECUTE tropicube_migration_statement;
DEALLOCATE PREPARE tropicube_migration_statement;

SET @tropicube_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'tropicube_mission_assignments'
      AND column_name = 'reward_reroll_tokens'
);
SET @tropicube_ddl = IF(
    @tropicube_column_exists = 0,
    'ALTER TABLE tropicube_mission_assignments ADD COLUMN reward_reroll_tokens SMALLINT NOT NULL DEFAULT 0 AFTER reroll_count',
    'SELECT 1'
);
PREPARE tropicube_migration_statement FROM @tropicube_ddl;
EXECUTE tropicube_migration_statement;
DEALLOCATE PREPARE tropicube_migration_statement;

CREATE TABLE IF NOT EXISTS tropicube_sheepwars_season_ratings (
    season_id BIGINT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    final_rating DOUBLE NOT NULL,
    final_uncertainty DOUBLE NOT NULL,
    final_tier VARCHAR(24) NOT NULL,
    ranked_matches INT NOT NULL DEFAULT 0,
    eligible BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at BIGINT NOT NULL,
    PRIMARY KEY (season_id, player_uuid),
    INDEX idx_sw_season_leaderboard (season_id, final_rating),
    FOREIGN KEY (season_id) REFERENCES tropicube_seasons(id) ON DELETE CASCADE,
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_profile_titles (
    player_uuid VARCHAR(36) NOT NULL,
    title_id VARCHAR(96) NOT NULL,
    display_key VARCHAR(191) NOT NULL,
    unlocked_at BIGINT NOT NULL,
    season_id BIGINT,
    PRIMARY KEY (player_uuid, title_id),
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (season_id) REFERENCES tropicube_seasons(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_profile_badges (
    player_uuid VARCHAR(36) NOT NULL,
    badge_id VARCHAR(96) NOT NULL,
    display_key VARCHAR(191) NOT NULL,
    unlocked_at BIGINT NOT NULL,
    season_id BIGINT,
    PRIMARY KEY (player_uuid, badge_id),
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (season_id) REFERENCES tropicube_seasons(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_player_comfort (
    player_uuid VARCHAR(36) PRIMARY KEY,
    selected_title_id VARCHAR(96),
    reroll_tokens SMALLINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_season_reward_grants (
    season_id BIGINT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    tier VARCHAR(24) NOT NULL,
    currency_amount DOUBLE NOT NULL,
    granted_at BIGINT NOT NULL,
    PRIMARY KEY (season_id, player_uuid),
    FOREIGN KEY (season_id) REFERENCES tropicube_seasons(id) ON DELETE CASCADE,
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_contextual_hints (
    player_uuid VARCHAR(36) NOT NULL,
    hint_id VARCHAR(64) NOT NULL,
    shown_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, hint_id),
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_privacy_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    requested_by VARCHAR(36) NOT NULL,
    request_type VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_at BIGINT NOT NULL,
    execute_after BIGINT,
    completed_at BIGINT,
    result_reference VARCHAR(191),
    INDEX idx_privacy_pending (status, execute_after),
    INDEX idx_privacy_player (player_uuid, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
