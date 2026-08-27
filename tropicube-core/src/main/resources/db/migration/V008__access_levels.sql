UPDATE tropicube_grades
SET default_vip_level = CASE name
        WHEN 'VIP' THEN 1 WHEN 'VIP_PLUS' THEN 2 WHEN 'PREMIUM' THEN 3
        WHEN 'HELPER' THEN 3 WHEN 'MODERATEUR' THEN 3 WHEN 'ADMIN' THEN 3 WHEN 'OWNER' THEN 3
        ELSE 0 END,
    default_mod_level = CASE name
        WHEN 'HELPER' THEN 1 WHEN 'MODERATEUR' THEN 2 WHEN 'ADMIN' THEN 3 WHEN 'OWNER' THEN 4
        ELSE 0 END;

CREATE TABLE IF NOT EXISTS tropicube_access_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    actor_uuid VARCHAR(36),
    source VARCHAR(32) NOT NULL,
    previous_grade VARCHAR(32),
    new_grade VARCHAR(32),
    previous_vip_level SMALLINT NOT NULL,
    new_vip_level SMALLINT NOT NULL,
    previous_mod_level SMALLINT NOT NULL,
    new_mod_level SMALLINT NOT NULL,
    revision BIGINT NOT NULL,
    changed_at BIGINT NOT NULL,
    INDEX idx_access_audit_target (target_uuid, changed_at),
    INDEX idx_access_audit_retention (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tropicube_access_audit(
    target_uuid, actor_uuid, source, previous_grade, new_grade,
    previous_vip_level, new_vip_level, previous_mod_level, new_mod_level, revision, changed_at
)
SELECT p.uuid, NULL, 'MIGRATION', p.grade, p.grade,
       p.vip_level, COALESCE(g.default_vip_level, 0),
       p.mod_level, COALESCE(g.default_mod_level, 0),
       p.access_revision + 1, UNIX_TIMESTAMP()
FROM tropicube_players p
LEFT JOIN tropicube_grades g ON g.name = p.grade
WHERE NOT EXISTS (
    SELECT 1 FROM tropicube_access_audit a
    WHERE a.target_uuid = p.uuid AND a.source = 'MIGRATION'
);

UPDATE tropicube_players p
JOIN tropicube_access_audit a ON a.target_uuid = p.uuid AND a.source = 'MIGRATION'
SET p.vip_level = a.new_vip_level,
    p.mod_level = a.new_mod_level,
    p.access_revision = GREATEST(p.access_revision, a.revision);

DROP TABLE IF EXISTS tropicube_permissions;

SET @tropicube_drop_column = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tropicube_grades' AND COLUMN_NAME = 'is_vip'),
    'ALTER TABLE tropicube_grades DROP COLUMN is_vip', 'DO 0');
PREPARE tropicube_drop_statement FROM @tropicube_drop_column;
EXECUTE tropicube_drop_statement;
DEALLOCATE PREPARE tropicube_drop_statement;

SET @tropicube_drop_column = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tropicube_grades' AND COLUMN_NAME = 'is_staff'),
    'ALTER TABLE tropicube_grades DROP COLUMN is_staff', 'DO 0');
PREPARE tropicube_drop_statement FROM @tropicube_drop_column;
EXECUTE tropicube_drop_statement;
DEALLOCATE PREPARE tropicube_drop_statement;

SET @tropicube_drop_column = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tropicube_grades' AND COLUMN_NAME = 'permissions'),
    'ALTER TABLE tropicube_grades DROP COLUMN permissions', 'DO 0');
PREPARE tropicube_drop_statement FROM @tropicube_drop_column;
EXECUTE tropicube_drop_statement;
DEALLOCATE PREPARE tropicube_drop_statement;

SET @tropicube_drop_column = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tropicube_players' AND COLUMN_NAME = 'vipLevel'),
    'ALTER TABLE tropicube_players DROP COLUMN vipLevel', 'DO 0');
PREPARE tropicube_drop_statement FROM @tropicube_drop_column;
EXECUTE tropicube_drop_statement;
DEALLOCATE PREPARE tropicube_drop_statement;

SET @tropicube_drop_column = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tropicube_players' AND COLUMN_NAME = 'staffLevel'),
    'ALTER TABLE tropicube_players DROP COLUMN staffLevel', 'DO 0');
PREPARE tropicube_drop_statement FROM @tropicube_drop_column;
EXECUTE tropicube_drop_statement;
DEALLOCATE PREPARE tropicube_drop_statement;
