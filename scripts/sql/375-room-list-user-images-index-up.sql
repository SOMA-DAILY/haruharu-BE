BEGIN;

-- 참여 중인 방 목록 조회(GET /api/v1/rooms, #375)의 대표이미지 일괄 조회를 커버하는 인덱스.
-- 쿼리: user_images WHERE user_id IN (...) AND deleted_at IS NULL ORDER BY user_id, sorted_order
--       (UserImageJpaRepository.findAllByUserIdInOrderByUserIdAscSortedOrderAsc)
-- 전역 soft-delete(deleted_at)라 활성 행만 대상으로 하는 partial 인덱스로 둔다.
CREATE INDEX IF NOT EXISTS idx_user_images_user_sorted_active
    ON user_images (user_id, sorted_order)
    WHERE deleted_at IS NULL;

-- 참고: 목록의 room_members WHERE user_id = ? 경로를 커버하는 (user_id, room_id) 인덱스는 #387이 추가한다.
-- 이 배포는 #387 인덱스 적용 이후에 하거나, 그전까지는 room_members user_id 선행 조회가 스캔임을 감수한다.

COMMIT;
