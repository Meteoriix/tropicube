-- V001 and V002 are immutable. This migration preserves their data while exposing only clan identifiers.
SET @tc_legacy_clan_tables = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'tropicube_guilds'
);
SET @tc_ddl = IF(
    @tc_legacy_clan_tables = 1,
    'RENAME TABLE tropicube_guilds TO tropicube_clans, tropicube_guild_members TO tropicube_clan_members, tropicube_guild_invites TO tropicube_clan_invites, tropicube_guild_audit TO tropicube_clan_audit, tropicube_guild_challenges TO tropicube_clan_challenges, tropicube_guild_season_scores TO tropicube_clan_season_scores',
    'SELECT 1'
);
PREPARE tropicube_migration_statement FROM @tc_ddl;
EXECUTE tropicube_migration_statement;
DEALLOCATE PREPARE tropicube_migration_statement;

SET @tc_legacy_index = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'tropicube_clans' AND index_name = 'idx_guild_owner'
);
SET @tc_ddl = IF(@tc_legacy_index = 1,
    'ALTER TABLE tropicube_clans RENAME INDEX idx_guild_owner TO idx_clan_owner', 'SELECT 1');
PREPARE tropicube_migration_statement FROM @tc_ddl;
EXECUTE tropicube_migration_statement;
DEALLOCATE PREPARE tropicube_migration_statement;

SET @tc_legacy_column = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tropicube_clan_members' AND column_name = 'guild_id'
);
SET @tc_ddl = IF(@tc_legacy_column = 1,
    'ALTER TABLE tropicube_clan_members RENAME COLUMN guild_id TO clan_id, RENAME INDEX idx_guild_succession TO idx_clan_succession',
    'SELECT 1');
PREPARE tropicube_migration_statement FROM @tc_ddl;
EXECUTE tropicube_migration_statement;
DEALLOCATE PREPARE tropicube_migration_statement;

SET @tc_legacy_column = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tropicube_clan_invites' AND column_name = 'guild_id'
);
SET @tc_ddl = IF(@tc_legacy_column = 1,
    'ALTER TABLE tropicube_clan_invites RENAME COLUMN guild_id TO clan_id, RENAME INDEX idx_guild_invite_expiry TO idx_clan_invite_expiry',
    'SELECT 1');
PREPARE tropicube_migration_statement FROM @tc_ddl;
EXECUTE tropicube_migration_statement;
DEALLOCATE PREPARE tropicube_migration_statement;

SET @tc_legacy_column = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tropicube_clan_audit' AND column_name = 'guild_id'
);
SET @tc_ddl = IF(@tc_legacy_column = 1,
    'ALTER TABLE tropicube_clan_audit RENAME COLUMN guild_id TO clan_id, RENAME INDEX idx_guild_audit TO idx_clan_audit',
    'SELECT 1');
PREPARE tropicube_migration_statement FROM @tc_ddl;
EXECUTE tropicube_migration_statement;
DEALLOCATE PREPARE tropicube_migration_statement;

SET @tc_legacy_column = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tropicube_clan_challenges' AND column_name = 'guild_id'
);
SET @tc_ddl = IF(@tc_legacy_column = 1,
    'ALTER TABLE tropicube_clan_challenges RENAME COLUMN guild_id TO clan_id, RENAME INDEX idx_guild_challenge_week TO idx_clan_challenge_week',
    'SELECT 1');
PREPARE tropicube_migration_statement FROM @tc_ddl;
EXECUTE tropicube_migration_statement;
DEALLOCATE PREPARE tropicube_migration_statement;

SET @tc_legacy_column = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tropicube_clan_season_scores' AND column_name = 'guild_id'
);
SET @tc_ddl = IF(@tc_legacy_column = 1,
    'ALTER TABLE tropicube_clan_season_scores RENAME COLUMN guild_id TO clan_id, RENAME INDEX idx_guild_season_ranking TO idx_clan_season_ranking',
    'SELECT 1');
PREPARE tropicube_migration_statement FROM @tc_ddl;
EXECUTE tropicube_migration_statement;
DEALLOCATE PREPARE tropicube_migration_statement;

UPDATE tropicube_notifications SET category = 'CLAN' WHERE category = 'GUILD';
UPDATE tropicube_notifications
SET message_key = CONCAT('clan.', SUBSTRING(message_key, 7))
WHERE message_key LIKE 'guild.%';
UPDATE tropicube_notifications
SET action_json = REPLACE(action_json, '/guild ', '/clan ')
WHERE action_json LIKE '%/guild %';
