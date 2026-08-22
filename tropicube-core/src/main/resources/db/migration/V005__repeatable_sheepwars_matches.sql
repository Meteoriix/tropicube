ALTER TABLE tropicube_sheepwars_matches DROP INDEX uk_match_instance;

CREATE INDEX idx_match_instance ON tropicube_sheepwars_matches(instance_id, ended_at);
