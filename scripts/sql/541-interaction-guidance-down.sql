-- #541. Stop/disable guidance first. Removes experiment evidence; apply only with explicit approval.
BEGIN;
DROP TABLE IF EXISTS room_interaction_guide_claims;
DROP TABLE IF EXISTS room_interaction_first_actions;
DROP TABLE IF EXISTS room_interaction_experiments;
COMMIT;
