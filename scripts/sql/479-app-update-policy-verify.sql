SELECT id, ios_minimum_version, android_minimum_version, revision, updated_by, updated_at
FROM app_update_policy WHERE id = 1;
SELECT count(*) AS policy_count FROM app_update_policy;
SELECT revision, previous_ios_minimum_version, previous_android_minimum_version,
       ios_minimum_version, android_minimum_version, changed_by, changed_at
FROM app_update_policy_history ORDER BY revision DESC LIMIT 20;
