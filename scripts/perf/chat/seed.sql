-- Local, disposable performance DB only. Does not delete or overwrite existing data.
\set ON_ERROR_STOP on
\if :{?room_count}
\else
  \set room_count 10000
\endif
\if :{?active_rooms}
\else
  \set active_rooms 50
\endif
\if :{?messages_per_room}
\else
  \set messages_per_room 10
\endif

BEGIN;
SET LOCAL TIME ZONE 'Asia/Seoul';
SELECT pg_advisory_xact_lock(502, 1);

CREATE TEMP TABLE perf_config ON COMMIT DROP AS
SELECT :'room_count'::integer AS room_count, :'active_rooms'::integer AS active_rooms,
       :'messages_per_room'::integer AS messages_per_room, localtimestamp AS seeded_at;

DO $$
DECLARE c record;
BEGIN
  IF current_database() <> 'haruharu_chat_perf' OR current_user <> 'chat_perf' THEN
    RAISE EXCEPTION 'Only the dedicated haruharu_chat_perf database / chat_perf role is allowed';
  END IF;
  SELECT * INTO c FROM perf_config;
  IF c.room_count NOT BETWEEN 2 AND 100000 OR c.active_rooms NOT BETWEEN 1 AND c.room_count - 1
      OR c.messages_per_room NOT BETWEEN 1 AND 100 THEN
    RAISE EXCEPTION 'Invalid fixture size';
  END IF;
  IF EXISTS (SELECT 1 FROM users) OR EXISTS (SELECT 1 FROM rooms)
      OR EXISTS (SELECT 1 FROM chat_rooms) OR EXISTS (SELECT 1 FROM room_members)
      OR EXISTS (SELECT 1 FROM chat_room_members) OR EXISTS (SELECT 1 FROM chat_messages) THEN
    RAISE EXCEPTION 'Fixture requires empty tables; existing data will not be modified';
  END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

CREATE TABLE chat_perf_fixture (
  singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
  seeded_at timestamp NOT NULL,
  room_count integer NOT NULL,
  active_rooms integer NOT NULL,
  members_per_room integer NOT NULL,
  messages_per_room integer NOT NULL
);
INSERT INTO chat_perf_fixture
SELECT true, seeded_at, room_count, active_rooms, 6, messages_per_room FROM perf_config;

INSERT INTO users (id, status, created_at, updated_at)
SELECT n, 'ACTIVE', c.seeded_at - interval '12 days', c.seeded_at - interval '12 days'
FROM perf_config c CROSS JOIN LATERAL generate_series(1, c.room_count * 6) AS n;

INSERT INTO rooms (id, status, scheduled_date, total_days, started_at, ended_at,
                   starts_at, final_selection_opens_at, final_selection_closes_at,
                   result_at, ends_at, created_at, updated_at)
SELECT n, CASE WHEN n <= c.active_rooms THEN 'ACTIVE' ELSE 'ENDED' END,
       t.starts_at::date, 3, t.starts_at,
       CASE WHEN n > c.active_rooms THEN t.starts_at + interval '3 days' END,
       t.starts_at, t.starts_at + interval '2 days', t.starts_at + interval '49 hours',
       t.starts_at + interval '50 hours', t.starts_at + interval '3 days',
       t.starts_at - interval '1 day', c.seeded_at
FROM perf_config c CROSS JOIN LATERAL generate_series(1, c.room_count) AS n
CROSS JOIN LATERAL (
  SELECT c.seeded_at - CASE WHEN n <= c.active_rooms THEN interval '1 day' ELSE interval '10 days' END AS starts_at
) t;

INSERT INTO chat_rooms (id, type, room_id, match_result_id, created_at, updated_at, close_at)
SELECT id, 'GROUP', id, NULL, starts_at, starts_at,
       CASE WHEN status = 'ENDED' THEN ended_at END FROM rooms;

INSERT INTO room_members (id, room_id, user_id, gender, pseudonym, birthday,
                          joined_at, created_at, updated_at)
SELECT (r.id - 1) * 6 + slot, r.id, (r.id - 1) * 6 + slot,
       CASE WHEN slot <= 3 THEN 'MALE' ELSE 'FEMALE' END,
       (ARRAY['민준', '서준', '도윤', '서아', '하윤', '지아'])[slot], NULL,
       r.starts_at - interval '5 minutes', r.starts_at - interval '5 minutes', r.starts_at
FROM rooms r CROSS JOIN generate_series(1, 6) AS slot;

INSERT INTO chat_room_members (id, chat_room_id, user_id, joined_at, left_at, last_read_message_id)
SELECT rm.id, cr.id, rm.user_id, cr.created_at, NULL, NULL
FROM room_members rm JOIN chat_rooms cr ON cr.room_id = rm.room_id;

INSERT INTO chat_messages (id, chat_room_id, sender_id, sender_type, content,
                           message_type, created_at, updated_at)
SELECT (r.id - 1) * c.messages_per_room + seq, cr.id,
       (r.id - 1) * 6 + ((seq - 1) % 6) + 1, 'USER',
       format('perf-seed room=%s seq=%s', r.id, seq), 'TEXT',
       r.starts_at + seq * interval '1 second', r.starts_at + seq * interval '1 second'
FROM rooms r JOIN chat_rooms cr ON cr.room_id = r.id
CROSS JOIN perf_config c CROSS JOIN LATERAL generate_series(1, c.messages_per_room) AS seq;

-- Native fixture inserts must leave IDENTITY sequences ready for real STOMP writes.
SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT max(id) FROM users));
SELECT setval(pg_get_serial_sequence('rooms', 'id'), (SELECT max(id) FROM rooms));
SELECT setval(pg_get_serial_sequence('room_members', 'id'), (SELECT max(id) FROM room_members));
SELECT setval(pg_get_serial_sequence('chat_rooms', 'id'), (SELECT max(id) FROM chat_rooms));
SELECT setval(pg_get_serial_sequence('chat_room_members', 'id'), (SELECT max(id) FROM chat_room_members));
SELECT setval(pg_get_serial_sequence('chat_messages', 'id'), (SELECT max(id) FROM chat_messages));
COMMIT;

ANALYZE users;
ANALYZE rooms;
ANALYZE room_members;
ANALYZE chat_rooms;
ANALYZE chat_room_members;
ANALYZE chat_messages;

SELECT room_count, active_rooms, room_count * members_per_room AS users,
       room_count * messages_per_room AS messages, seeded_at
FROM chat_perf_fixture;
