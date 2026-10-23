-- Apply to the target database before deploying the photo-capable app/admin.
-- Existing TEXT/MISSION rows are preserved. Pause writers during constraint replacement.
BEGIN;
LOCK TABLE chat_messages IN ACCESS EXCLUSIVE MODE;
CREATE TABLE chat_photo_constraints_backup_538 AS
SELECT c.conname AS name, pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='message_type'
WHERE c.conrelid='chat_messages'::regclass AND c.contype='c' AND a.attnum=ANY(c.conkey);
ALTER TABLE chat_messages ADD COLUMN photo_key varchar(200);
ALTER TABLE chat_messages ADD COLUMN photo_width integer;
ALTER TABLE chat_messages ADD COLUMN photo_height integer;
ALTER TABLE chat_messages ADD COLUMN photo_digest varchar(64);
DO $$ DECLARE previous record; BEGIN
  FOR previous IN SELECT name FROM chat_photo_constraints_backup_538 LOOP
    EXECUTE format('ALTER TABLE chat_messages DROP CONSTRAINT %I', previous.name);
  END LOOP;
END $$;
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_mission_reference CHECK (
  (message_type IN ('TEXT','IMAGE') AND room_mission_id IS NULL AND mission_announcement_id IS NULL)
  OR (message_type='MISSION' AND sender_type='SYSTEM' AND room_mission_id IS NOT NULL AND room_mission_id>0
    AND mission_announcement_id IS NOT NULL AND mission_announcement_id>0)
);
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_photo CHECK (
  (message_type='IMAGE' AND sender_type='USER' AND client_request_id IS NOT NULL
    AND photo_key IS NOT NULL AND photo_width IS NOT NULL AND photo_width>0
    AND photo_height IS NOT NULL AND photo_height>0 AND photo_digest IS NOT NULL)
  OR (message_type<>'IMAGE' AND photo_key IS NULL AND photo_width IS NULL AND photo_height IS NULL AND photo_digest IS NULL)
);
COMMIT;
