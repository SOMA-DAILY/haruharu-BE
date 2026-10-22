-- Read-only verification. Never changes operational experiment assignments.
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_name IN (
    'room_interaction_experiments', 'room_interaction_first_actions', 'room_interaction_guide_claims'
) ORDER BY table_name;
SELECT conname, pg_get_constraintdef(oid) AS definition FROM pg_constraint
WHERE conrelid = 'room_interaction_guide_claims'::regclass ORDER BY conname;
SELECT experiment_key, variant, count(*) AS rooms FROM room_interaction_experiments
GROUP BY experiment_key, variant ORDER BY experiment_key, variant;
