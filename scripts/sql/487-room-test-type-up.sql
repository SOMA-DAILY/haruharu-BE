BEGIN;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS is_test boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS idx_rooms_is_test_id ON rooms (is_test, id);
COMMIT;
