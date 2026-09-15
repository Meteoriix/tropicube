CREATE TABLE IF NOT EXISTS tropicube_fallenkingdoms_preferences (
    player_uuid CHAR(36) NOT NULL,
    kit_id VARCHAR(32) NULL,
    kingdom_id VARCHAR(16) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid)
);
