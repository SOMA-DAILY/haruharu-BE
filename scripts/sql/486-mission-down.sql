-- Offline rollback ONLY before any new runtime writes. Keep backups for investigation; do not rerun up blindly.
BEGIN;
LOCK TABLE room_schedule_items, room_mission_instances, announcement_missions, mission_announcements, chat_messages IN ACCESS EXCLUSIVE MODE;
DO $$
BEGIN
    IF EXISTS ((SELECT * FROM room_mission_instances EXCEPT SELECT * FROM room_mission_instances_backup_486)
        UNION ALL (SELECT * FROM room_mission_instances_backup_486 EXCEPT SELECT * FROM room_mission_instances))
        OR EXISTS ((SELECT * FROM announcement_missions EXCEPT SELECT * FROM announcement_missions_backup_486)
        UNION ALL (SELECT * FROM announcement_missions_backup_486 EXCEPT SELECT * FROM announcement_missions))
        OR EXISTS ((SELECT * FROM mission_announcements EXCEPT SELECT * FROM mission_announcements_backup_486)
        UNION ALL (SELECT * FROM mission_announcements_backup_486 EXCEPT SELECT * FROM mission_announcements))
        OR EXISTS ((SELECT * FROM room_schedule_items EXCEPT SELECT * FROM room_schedule_mission_backup_486)
        UNION ALL (SELECT * FROM room_schedule_mission_backup_486 EXCEPT SELECT * FROM room_schedule_items)) THEN
        RAISE EXCEPTION 'Runtime mission/schedule changes exist; manual reconciliation required';
    END IF;
    IF (SELECT count(*) FROM chat_messages) <> (SELECT count(*) FROM chat_message_mission_backup_486)
        OR EXISTS (SELECT 1 FROM chat_message_mission_backup_486 b LEFT JOIN chat_messages c ON c.id=b.id
            WHERE c.id IS NULL OR (to_jsonb(c)-ARRAY['message_type','room_mission_id','mission_announcement_id'])
                IS DISTINCT FROM (to_jsonb(b)-'message_type')
            OR c.room_mission_id IS DISTINCT FROM b.schedule_item_id
            OR c.mission_announcement_id IS DISTINCT FROM b.schedule_item_id) THEN
        RAISE EXCEPTION 'Runtime chat changes exist; manual reconciliation required';
    END IF;
END $$;
ALTER TABLE chat_messages DROP CONSTRAINT fk_chat_messages_mission_announcement;
ALTER TABLE chat_messages DROP CONSTRAINT ck_chat_messages_mission_reference;
UPDATE chat_messages c SET message_type=b.message_type FROM chat_message_mission_backup_486 b WHERE c.id=b.id;
ALTER TABLE chat_messages DROP COLUMN mission_announcement_id;
ALTER TABLE chat_messages DROP COLUMN room_mission_id;
DO $$
DECLARE original record;
BEGIN
    FOR original IN SELECT name,definition FROM chat_message_type_constraints_backup_486 LOOP
        EXECUTE format('ALTER TABLE chat_messages ADD CONSTRAINT %I %s',original.name,original.definition);
    END LOOP;
END $$;
DROP TABLE mission_announcements;
DROP TABLE announcement_missions;
DROP TABLE room_mission_instances;
COMMIT;
