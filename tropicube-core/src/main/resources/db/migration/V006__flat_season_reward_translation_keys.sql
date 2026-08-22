UPDATE tropicube_profile_titles
SET display_key = REPLACE(display_key, 'season.reward-title.', 'season.reward-title-')
WHERE display_key LIKE 'season.reward-title.%';

UPDATE tropicube_profile_badges
SET display_key = REPLACE(display_key, 'season.reward-badge.', 'season.reward-badge-')
WHERE display_key LIKE 'season.reward-badge.%';
