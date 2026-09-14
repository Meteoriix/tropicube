CREATE TABLE IF NOT EXISTS tropicube_game_results (
    match_id CHAR(36) NOT NULL,
    game_id VARCHAR(32) NOT NULL,
    end_cause VARCHAR(32) NOT NULL,
    winners VARCHAR(255) NOT NULL,
    ended_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (match_id),
    INDEX idx_game_results_game_ended (game_id, ended_at)
);

CREATE TABLE IF NOT EXISTS tropicube_game_result_players (
    match_id CHAR(36) NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    winner BOOLEAN NOT NULL,
    draw BOOLEAN NOT NULL,
    eliminations INT UNSIGNED NOT NULL,
    deaths INT UNSIGNED NOT NULL,
    objectives INT UNSIGNED NOT NULL,
    PRIMARY KEY (match_id, player_uuid),
    INDEX idx_game_result_players_player (player_uuid, match_id),
    CONSTRAINT fk_game_result_players_match FOREIGN KEY (match_id)
        REFERENCES tropicube_game_results(match_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tropicube_game_statistics (
    player_uuid CHAR(36) NOT NULL,
    game_id VARCHAR(32) NOT NULL,
    games BIGINT UNSIGNED NOT NULL DEFAULT 0,
    wins BIGINT UNSIGNED NOT NULL DEFAULT 0,
    draws BIGINT UNSIGNED NOT NULL DEFAULT 0,
    eliminations BIGINT UNSIGNED NOT NULL DEFAULT 0,
    deaths BIGINT UNSIGNED NOT NULL DEFAULT 0,
    objectives BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, game_id),
    INDEX idx_game_statistics_game_wins (game_id, wins)
);
