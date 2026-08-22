ALTER TABLE tropicube_guild_members
    ADD COLUMN IF NOT EXISTS contribution_week VARCHAR(16) NOT NULL DEFAULT '' AFTER last_active_at;

CREATE TABLE IF NOT EXISTS tropicube_guild_challenges (
    guild_id BIGINT NOT NULL,
    week_key VARCHAR(16) NOT NULL,
    challenge_id VARCHAR(64) NOT NULL,
    progress BIGINT NOT NULL DEFAULT 0,
    target BIGINT NOT NULL,
    completed_at BIGINT,
    PRIMARY KEY (guild_id, week_key, challenge_id),
    INDEX idx_guild_challenge_week (week_key, completed_at),
    FOREIGN KEY (guild_id) REFERENCES tropicube_guilds(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tropicube_guild_season_scores (
    guild_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    score DOUBLE NOT NULL DEFAULT 0,
    ranked_matches INT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (guild_id, season_id),
    INDEX idx_guild_season_ranking (season_id, score),
    FOREIGN KEY (guild_id) REFERENCES tropicube_guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (season_id) REFERENCES tropicube_seasons(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
