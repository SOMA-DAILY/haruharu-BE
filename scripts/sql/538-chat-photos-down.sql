-- Only before accepting any photo message. Never delete messages to force a rollback.
BEGIN;
LOCK TABLE chat_messages IN ACCESS EXCLUSIVE MODE;
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM chat_messages WHERE message_type='IMAGE' OR photo_key IS NOT NULL) THEN
    RAISE EXCEPTION 'Photo messages exist; keep the compatible backend and investigate rollback manually';
  END IF;
END $$;
ALTER TABLE chat_messages DROP CONSTRAINT ck_chat_messages_photo;
ALTER TABLE chat_messages DROP CONSTRAINT ck_chat_messages_mission_reference;
DO $$ DECLARE previous record; BEGIN
  FOR previous IN SELECT * FROM chat_photo_constraints_backup_538 LOOP
    EXECUTE format('ALTER TABLE chat_messages ADD CONSTRAINT %I %s', previous.name, previous.definition);
  END LOOP;
END $$;
ALTER TABLE chat_messages DROP COLUMN photo_key, DROP COLUMN photo_width, DROP COLUMN photo_height, DROP COLUMN photo_digest;
DROP TABLE chat_photo_constraints_backup_538;
COMMIT;
