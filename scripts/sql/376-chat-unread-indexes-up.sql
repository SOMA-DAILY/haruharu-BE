BEGIN;

-- AFTER_COMMIT 동기화가 한 번 실패했던 방을 배포 시점에 복구한다. 런타임 synchronizeGroupRoom과 동일하게
-- 진행·최종선택·종료(ACTIVE·SELECTING·ENDED) 방을 대상으로 한다 — PENDING(아직 시작 전)·CANCELLED만 제외.
-- 종료된 방도 채팅 이력이 조회되므로, 시작 스냅샷 이후 늦게 입장한 멤버의 membership 누락을 함께 메운다.
-- 기존에 나간 chat membership은 UNIQUE 충돌로 그대로 두어 재활성화하지 않는다.
INSERT INTO chat_room_members (
    chat_room_id, user_id, joined_at, left_at, last_read_message_id)
SELECT chat_room.id, room_member.user_id, room_member.joined_at, NULL, NULL
FROM chat_rooms chat_room
JOIN rooms room
  ON room.id = chat_room.room_id
JOIN room_members room_member
  ON room_member.room_id = room.id
WHERE chat_room.type = 'GROUP'
  AND chat_room.deleted_at IS NULL
  AND room.deleted_at IS NULL
  AND room.status IN ('ACTIVE', 'SELECTING', 'ENDED')
  AND room_member.deleted_at IS NULL
ON CONFLICT (chat_room_id, user_id) DO NOTHING;

-- Existing GROUP memberships predate the room-member timestamp invariant.
-- Normalize them before the joined-at privacy boundary is used by reads.
UPDATE chat_room_members membership
SET joined_at = room_member.joined_at
FROM chat_rooms chat_room
JOIN room_members room_member
  ON room_member.room_id = chat_room.room_id
WHERE membership.chat_room_id = chat_room.id
  AND membership.user_id = room_member.user_id
  AND chat_room.type = 'GROUP'
  AND chat_room.deleted_at IS NULL
  AND room_member.deleted_at IS NULL
  AND membership.joined_at IS DISTINCT FROM room_member.joined_at;

CREATE INDEX IF NOT EXISTS idx_chat_room_members_active_user_room
    ON chat_room_members (user_id, chat_room_id)
    WHERE left_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_room_created_id
    ON chat_messages (chat_room_id, created_at, id)
    WHERE deleted_at IS NULL;

COMMIT;
