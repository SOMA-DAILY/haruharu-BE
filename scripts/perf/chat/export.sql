\set ON_ERROR_STOP on
-- Export IDs only. fixture.py adds locally signed test JWTs to the ignored output file.
SELECT json_build_object(
  'dataset', (SELECT row_to_json(f) FROM chat_perf_fixture f),
  'rooms', (SELECT json_agg(row_to_json(r) ORDER BY r.room_id) FROM (
    SELECT cr.room_id, cr.id AS chat_room_id, r.status, r.starts_at, r.ends_at
    FROM chat_rooms cr JOIN rooms r ON r.id = cr.room_id
    WHERE r.id <= (SELECT active_rooms + 1 FROM chat_perf_fixture)
  ) r),
  'users', (SELECT json_agg(row_to_json(u) ORDER BY u.room_id, u.user_id) FROM (
    SELECT cm.user_id, cr.room_id, cm.chat_room_id, r.status, rm.pseudonym
    FROM chat_room_members cm
    JOIN chat_rooms cr ON cr.id = cm.chat_room_id
    JOIN rooms r ON r.id = cr.room_id
    JOIN room_members rm ON rm.room_id = r.id AND rm.user_id = cm.user_id
    WHERE r.id <= (SELECT active_rooms + 1 FROM chat_perf_fixture)
      AND cm.left_at IS NULL
  ) u)
);
