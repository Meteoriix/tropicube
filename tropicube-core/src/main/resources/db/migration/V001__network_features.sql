CREATE TABLE IF NOT EXISTS tropicube_player_preferences (
    player_uuid VARCHAR(36) PRIMARY KEY,
    profile_visibility VARCHAR(24) NOT NULL DEFAULT 'SUMMARY',
    message_privacy VARCHAR(24) NOT NULL DEFAULT 'EVERYONE',
    global_chat_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    lobby_visibility VARCHAR(24) NOT NULL DEFAULT 'EVERYONE',
    contextual_help BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    category VARCHAR(32) NOT NULL,
    message_key VARCHAR(191) NOT NULL,
    arguments_json TEXT NOT NULL,
    action_json TEXT,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    read_at BIGINT,
    INDEX idx_notification_inbox (player_uuid, read_at, created_at),
    INDEX idx_notification_expiry (expires_at),
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_private_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_uuid VARCHAR(36) NOT NULL,
    recipient_uuid VARCHAR(36) NOT NULL,
    body VARCHAR(512) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    delivered_at BIGINT,
    INDEX idx_pm_recipient (recipient_uuid, delivered_at, created_at),
    INDEX idx_pm_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_ignored_players (
    owner_uuid VARCHAR(36) NOT NULL,
    ignored_uuid VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (owner_uuid, ignored_uuid),
    FOREIGN KEY (owner_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (ignored_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_uuid VARCHAR(36) NOT NULL,
    target_uuid VARCHAR(36) NOT NULL,
    category VARCHAR(32) NOT NULL,
    details VARCHAR(512),
    instance_id VARCHAR(64),
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    assigned_staff_uuid VARCHAR(36),
    resolution VARCHAR(512),
    created_at BIGINT NOT NULL,
    resolved_at BIGINT,
    evidence_expires_at BIGINT NOT NULL,
    INDEX idx_report_queue (status, created_at),
    INDEX idx_report_target (target_uuid, created_at),
    INDEX idx_report_evidence_expiry (evidence_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_report_evidence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    author_uuid VARCHAR(36) NOT NULL,
    body VARCHAR(512) NOT NULL,
    context_json TEXT NOT NULL,
    instance_id VARCHAR(64),
    sent_at BIGINT NOT NULL,
    captured_at BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    UNIQUE KEY uk_report_message (report_id, message_id),
    FOREIGN KEY (report_id) REFERENCES tropicube_reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_staff_totp (
    player_uuid VARCHAR(36) PRIMARY KEY,
    encrypted_secret TEXT NOT NULL,
    recovery_hashes TEXT NOT NULL,
    enabled_at BIGINT NOT NULL,
    last_used_step BIGINT NOT NULL DEFAULT -1,
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_network_progression (
    player_uuid VARCHAR(36) PRIMARY KEY,
    experience BIGINT NOT NULL DEFAULT 0,
    level INT NOT NULL DEFAULT 1,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_mission_assignments (
    player_uuid VARCHAR(36) NOT NULL,
    rotation_type VARCHAR(16) NOT NULL,
    rotation_key VARCHAR(24) NOT NULL,
    slot_index SMALLINT NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    target BIGINT NOT NULL,
    completed_at BIGINT,
    rewarded_at BIGINT,
    reroll_count SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, rotation_type, rotation_key, slot_index),
    INDEX idx_mission_active (player_uuid, rotation_key),
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_seasons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    season_key VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(64) NOT NULL,
    starts_at BIGINT NOT NULL,
    ends_at BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    INDEX idx_season_status (status, starts_at, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_guilds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    tag VARCHAR(8) NOT NULL UNIQUE,
    owner_uuid VARCHAR(36) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    experience BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    INDEX idx_guild_owner (owner_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_guild_members (
    guild_id BIGINT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL UNIQUE,
    role VARCHAR(16) NOT NULL,
    joined_at BIGINT NOT NULL,
    last_active_at BIGINT NOT NULL,
    weekly_contribution BIGINT NOT NULL DEFAULT 0,
    total_contribution BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, player_uuid),
    INDEX idx_guild_succession (guild_id, last_active_at, joined_at),
    FOREIGN KEY (guild_id) REFERENCES tropicube_guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_guild_invites (
    guild_id BIGINT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    invited_by VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, player_uuid),
    INDEX idx_guild_invite_expiry (expires_at),
    FOREIGN KEY (guild_id) REFERENCES tropicube_guilds(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_guild_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    actor_uuid VARCHAR(36),
    action_type VARCHAR(32) NOT NULL,
    details_json TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_guild_audit (guild_id, created_at),
    FOREIGN KEY (guild_id) REFERENCES tropicube_guilds(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_sheepwars_ratings (
    player_uuid VARCHAR(36) PRIMARY KEY,
    rating DOUBLE NOT NULL DEFAULT 1500,
    uncertainty DOUBLE NOT NULL DEFAULT 350,
    placements_remaining SMALLINT NOT NULL DEFAULT 5,
    season_id BIGINT,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (season_id) REFERENCES tropicube_seasons(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_sheepwars_matches (
    id CHAR(36) PRIMARY KEY,
    instance_id VARCHAR(64) NOT NULL,
    season_id BIGINT,
    mode VARCHAR(24) NOT NULL,
    map_id VARCHAR(64) NOT NULL,
    winning_team VARCHAR(16),
    started_at BIGINT NOT NULL,
    ended_at BIGINT NOT NULL,
    timeline_json MEDIUMTEXT NOT NULL,
    UNIQUE KEY uk_match_instance (instance_id),
    INDEX idx_match_season (season_id, ended_at),
    FOREIGN KEY (season_id) REFERENCES tropicube_seasons(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_sheepwars_match_players (
    match_id CHAR(36) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    team VARCHAR(16) NOT NULL,
    kit VARCHAR(32) NOT NULL,
    kills INT NOT NULL DEFAULT 0,
    deaths INT NOT NULL DEFAULT 0,
    sheep_launched INT NOT NULL DEFAULT 0,
    damage_dealt DOUBLE NOT NULL DEFAULT 0,
    support_score DOUBLE NOT NULL DEFAULT 0,
    rating_before DOUBLE,
    rating_after DOUBLE,
    network_xp BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (match_id, player_uuid),
    INDEX idx_match_player_history (player_uuid, match_id),
    FOREIGN KEY (match_id) REFERENCES tropicube_sheepwars_matches(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_sheepwars_kit_mastery (
    player_uuid VARCHAR(36) NOT NULL,
    kit_id VARCHAR(32) NOT NULL,
    experience BIGINT NOT NULL DEFAULT 0,
    level INT NOT NULL DEFAULT 1,
    selected_branch VARCHAR(32),
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (player_uuid, kit_id),
    FOREIGN KEY (player_uuid) REFERENCES tropicube_players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
