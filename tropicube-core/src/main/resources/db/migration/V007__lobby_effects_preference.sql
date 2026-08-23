SET @tropicube_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'tropicube_player_preferences'
      AND column_name = 'lobby_effects_enabled'
);
SET @tropicube_ddl = IF(
    @tropicube_column_exists = 0,
    'ALTER TABLE tropicube_player_preferences ADD COLUMN lobby_effects_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER contextual_help',
    'SELECT 1'
);
PREPARE tropicube_migration_statement FROM @tropicube_ddl;
EXECUTE tropicube_migration_statement;
DEALLOCATE PREPARE tropicube_migration_statement;
