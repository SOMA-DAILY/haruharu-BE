-- #496 rollback: only while no RECORDING mission exists. Cancel or migrate them first; this never deletes rows.
BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM room_mission_instances WHERE type='RECORDING') THEN
        RAISE EXCEPTION 'RECORDING missions exist; manual reconciliation required';
    END IF;
END $$;
ALTER TABLE room_mission_instances DROP CONSTRAINT IF EXISTS room_mission_instances_type_check;
ALTER TABLE room_mission_instances ADD CONSTRAINT room_mission_instances_type_check
    CHECK (type IN ('ANNOUNCEMENT','PROFILE_REVEAL'));
COMMIT;
