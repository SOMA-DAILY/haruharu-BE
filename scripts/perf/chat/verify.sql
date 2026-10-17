\set ON_ERROR_STOP on
SET TIME ZONE 'Asia/Seoul';

DO $$
DECLARE c record;
BEGIN
  IF current_database() <> 'haruharu_chat_perf' THEN
    RAISE EXCEPTION 'Not the local performance database';
  END IF;
  SELECT * INTO STRICT c FROM chat_perf_fixture;
  IF (SELECT count(*) FROM rooms) <> c.room_count
      OR (SELECT count(*) FROM chat_rooms) <> c.room_count
      OR (SELECT count(*) FROM users) <> c.room_count * c.members_per_room
      OR (SELECT count(*) FROM room_members) <> c.room_count * c.members_per_room
      OR (SELECT count(*) FROM chat_room_members) <> c.room_count * c.members_per_room
      OR (SELECT count(*) FROM chat_messages WHERE content LIKE 'perf-seed room=%')
         <> c.room_count * c.messages_per_room THEN
    RAISE EXCEPTION 'Fixture row counts do not match the manifest';
  END IF;
  IF EXISTS (
    SELECT 1 FROM chat_room_members cm
    LEFT JOIN chat_rooms cr ON cr.id = cm.chat_room_id
    LEFT JOIN rooms r ON r.id = cr.room_id
    LEFT JOIN users u ON u.id = cm.user_id
    LEFT JOIN room_members rm ON rm.room_id = r.id AND rm.user_id = u.id
    WHERE cr.id IS NULL OR r.id IS NULL OR u.id IS NULL OR rm.id IS NULL
      OR cm.left_at IS NOT NULL OR rm.pseudonym IS NULL
  ) THEN
    RAISE EXCEPTION 'Invalid participant mapping';
  END IF;
  IF EXISTS (SELECT 1 FROM chat_room_members GROUP BY chat_room_id HAVING count(*) <> c.members_per_room)
      OR EXISTS (SELECT 1 FROM room_members GROUP BY room_id HAVING count(*) <> c.members_per_room) THEN
    RAISE EXCEPTION 'Each group must have exactly six participants';
  END IF;
  IF EXISTS (
    SELECT 1 FROM chat_messages m
    LEFT JOIN chat_room_members cm ON cm.chat_room_id = m.chat_room_id AND cm.user_id = m.sender_id
    WHERE m.sender_type = 'USER' AND cm.id IS NULL
  ) THEN
    RAISE EXCEPTION 'Message sender is not a room participant';
  END IF;
  IF (SELECT count(*) FROM rooms WHERE status = 'ACTIVE' AND starts_at <= localtimestamp
      AND final_selection_opens_at > localtimestamp AND ends_at > localtimestamp) <> c.active_rooms
      OR (SELECT count(*) FROM rooms WHERE status = 'ENDED' AND ends_at < localtimestamp)
         <> c.room_count - c.active_rooms THEN
    RAISE EXCEPTION 'Unexpected or expired room schedules';
  END IF;
  IF EXISTS (SELECT 1 FROM device_tokens) THEN
    RAISE EXCEPTION 'Performance accounts must not have push device tokens';
  END IF;
  IF EXISTS (
    SELECT 1 FROM chat_rooms c JOIN rooms r ON r.id = c.room_id
    WHERE (r.status = 'ENDED' AND c.close_at IS NULL)
       OR (r.status = 'ACTIVE' AND c.close_at IS NOT NULL)
  ) THEN
    RAISE EXCEPTION 'Chat closure does not match fixture room status';
  END IF;
END $$;

SELECT 'PASS' AS fixture_integrity,
       (SELECT count(*) FROM rooms) AS rooms,
       (SELECT count(*) FROM rooms WHERE status = 'ACTIVE') AS active_rooms,
       (SELECT count(*) FROM users) AS users,
       (SELECT count(*) FROM chat_messages WHERE content LIKE 'perf-seed room=%') AS seed_messages,
       (SELECT min(ends_at) FROM rooms WHERE status = 'ACTIVE') AS active_ends_at;
SELECT indexname, indexdef FROM pg_indexes
WHERE schemaname = 'public' AND tablename IN ('rooms', 'chat_rooms') ORDER BY tablename, indexname;
