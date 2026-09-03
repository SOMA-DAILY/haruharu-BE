SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = current_schema()
  AND (
      (tablename = 'chat_room_members'
       AND indexname = 'idx_chat_room_members_active_user_room')
      OR
      (tablename = 'chat_messages'
       AND indexname IN ('idx_chat_messages_room_id', 'idx_chat_messages_room_created_id'))
  )
ORDER BY tablename, indexname;

SELECT room_id, COUNT(*) AS duplicate_group_room_count
FROM chat_rooms
WHERE type = 'GROUP'
  AND deleted_at IS NULL
GROUP BY room_id
HAVING COUNT(*) > 1;

SELECT membership.chat_room_id, membership.user_id, COUNT(*) AS duplicate_membership_count
FROM chat_room_members membership
JOIN chat_rooms chat_room ON chat_room.id = membership.chat_room_id
WHERE chat_room.type = 'GROUP'
  AND chat_room.deleted_at IS NULL
GROUP BY membership.chat_room_id, membership.user_id
HAVING COUNT(*) > 1;

SELECT COUNT(*) AS null_joined_at_boundary_count
FROM chat_room_members membership
JOIN chat_rooms chat_room ON chat_room.id = membership.chat_room_id
WHERE chat_room.type = 'GROUP'
  AND chat_room.deleted_at IS NULL
  AND membership.joined_at IS NULL;

SELECT membership.chat_room_id, membership.user_id,
       membership.joined_at AS chat_joined_at,
       room_member.joined_at AS room_joined_at
FROM chat_room_members membership
JOIN chat_rooms chat_room ON chat_room.id = membership.chat_room_id
JOIN room_members room_member
  ON room_member.room_id = chat_room.room_id
 AND room_member.user_id = membership.user_id
WHERE chat_room.type = 'GROUP'
  AND chat_room.deleted_at IS NULL
  AND room_member.deleted_at IS NULL
  AND membership.joined_at IS DISTINCT FROM room_member.joined_at;

SELECT room.id AS room_id, room_member.user_id
FROM rooms room
JOIN chat_rooms chat_room
  ON chat_room.room_id = room.id
 AND chat_room.type = 'GROUP'
 AND chat_room.deleted_at IS NULL
JOIN room_members room_member
  ON room_member.room_id = room.id
 AND room_member.deleted_at IS NULL
LEFT JOIN chat_room_members membership
  ON membership.chat_room_id = chat_room.id
 AND membership.user_id = room_member.user_id
WHERE room.status IN ('ACTIVE', 'SELECTING')
  AND room.deleted_at IS NULL
  AND membership.id IS NULL;
