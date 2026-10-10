-- #496: allow the RECORDING mission type on databases where room_mission_instances already exists
-- (created by 486-mission-up.sql or by Hibernate). Safe to rerun. New installs get the same list from 486-mission-up.sql.
BEGIN;
DO $$
DECLARE existing record;
BEGIN
    IF to_regclass('room_mission_instances') IS NULL THEN
        RAISE EXCEPTION 'room_mission_instances does not exist; run 486-mission-up.sql first';
    END IF;
    -- The value-list CHECK may carry a generated name. Replace every single-column CHECK on "type".
    FOR existing IN
        SELECT c.conname FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='type'
        WHERE c.conrelid='room_mission_instances'::regclass AND c.contype='c'
            AND cardinality(c.conkey)=1 AND a.attnum=ANY(c.conkey)
    LOOP
        EXECUTE format('ALTER TABLE room_mission_instances DROP CONSTRAINT %I', existing.conname);
    END LOOP;
END $$;
ALTER TABLE room_mission_instances ADD CONSTRAINT room_mission_instances_type_check
    CHECK (type IN ('ANNOUNCEMENT','PROFILE_REVEAL','RECORDING'));
COMMIT;
