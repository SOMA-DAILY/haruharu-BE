package org.son.haruharu.domain.user.storage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.son.haruharu.domain.user.implement.WithdrawnUserPurgeRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 탈퇴 회원 파기 native SQL(#555). 전부 user_id 하나로 묶이는 단순 DELETE/UPDATE 라 JPA 엔티티를 거치지 않는다
 * (소프트삭제 필터가 끼면 지워진 프로필 행을 못 본다). 테이블 이름은 각 Entity 의 @Table 과 같다.
 */
@Repository
public class WithdrawnUserPurgeRepositoryImpl implements WithdrawnUserPurgeRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> findPurgeCandidates(LocalDateTime withdrawnBefore, long afterId, int limit) {
        List<Number> rows = em.createNativeQuery(
                        "SELECT id FROM users WHERE status = 'DELETED' AND purged_at IS NULL AND updated_at <= :threshold "
                                + "AND id > :afterId ORDER BY id")
                .setParameter("threshold", withdrawnBefore)
                .setParameter("afterId", afterId)
                .setMaxResults(limit)
                .getResultList();
        return rows.stream().map(Number::longValue).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> findVideoUrls(Long userId) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT v.video_url, v.thumbnail_url, v.feed_video_url FROM video_posts v "
                                + "JOIN room_members m ON m.id = v.room_member_id WHERE m.user_id = :userId")
                .setParameter("userId", userId)
                .getResultList();
        return rows.stream()
                .flatMap(row -> java.util.Arrays.stream(row))
                .filter(v -> v != null)
                .map(String::valueOf)
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> findChatPhotoKeys(Long userId) {
        return em.createNativeQuery(
                        "SELECT photo_key FROM chat_messages WHERE sender_id = :userId AND photo_key IS NOT NULL")
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    public Map<String, Integer> purgePersonalData(Long userId, LocalDateTime now) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        // 영상: 반응 → 게시물 → 업로드 세션(캡션) 순. 영상 행은 통째로 지운다(캡션 포함).
        counts.put("video_reactions", execute(
                "DELETE FROM video_reactions WHERE video_post_id IN (SELECT v.id FROM video_posts v "
                        + "JOIN room_members m ON m.id = v.room_member_id WHERE m.user_id = :userId)", userId));
        counts.put("video_posts", execute(
                "DELETE FROM video_posts WHERE room_member_id IN (SELECT id FROM room_members WHERE user_id = :userId)",
                userId));
        counts.put("video_upload_sessions", execute("DELETE FROM video_upload_sessions WHERE user_id = :userId", userId));
        // 채팅: 사진 메시지는 "삭제된 메시지" 텍스트로 바꿔 객체 참조를 없애고 행·순서는 남긴다(답장 인용 유지).
        // ck_chat_messages_photo 가 IMAGE 행에 사진 칸을 강제하므로 유형도 TEXT 로 바꾼다. 운영자 삭제와 같은 표시 경로를 타도록
        // admin_deleted_at 을 찍되, 방송은 끝난 방이라 delete_broadcast_at 도 같이 찍어 방송 잡이 집지 않게 한다.
        // 텍스트 본문은 남긴다(가명 통계). 보낸이 식별자는 users 행과 같이 5년 남는 내부 번호다.
        counts.put("chat_message_photos", em.createNativeQuery(
                        "UPDATE chat_messages SET message_type = 'TEXT', content = :deletedContent, "
                                + "photo_key = NULL, photo_width = NULL, photo_height = NULL, photo_digest = NULL, "
                                + "admin_deleted_at = COALESCE(admin_deleted_at, :now), "
                                + "delete_broadcast_at = COALESCE(delete_broadcast_at, :now) "
                                + "WHERE sender_id = :userId AND photo_key IS NOT NULL")
                .setParameter("deletedContent", "삭제된 메시지") // = ChatMessage.DELETED_CONTENT (도메인 간 storage 참조 금지라 리터럴)
                .setParameter("now", now)
                .setParameter("userId", userId)
                .executeUpdate());
        // 익명 질문: 받는 사람 닉네임 스냅샷만 비운다.
        counts.put("anonymous_question_nicknames", execute(
                "UPDATE anonymous_questions SET recipient_nickname = '' WHERE recipient_user_id = :userId", userId));
        // 방 멤버: PASS 생년월일 스냅샷만 비우고 가명·성별·참여 이력은 남긴다.
        counts.put("room_member_birthdays", execute(
                "UPDATE room_members SET birthday = NULL WHERE user_id = :userId AND birthday IS NOT NULL", userId));
        // 프로필·가치관·사진 행(탈퇴 때 소프트삭제됨, S3 이미지는 그때 이미 삭제).
        counts.put("user_images", execute("DELETE FROM user_images WHERE user_id = :userId", userId));
        counts.put("user_profiles", execute("DELETE FROM user_profiles WHERE user_id = :userId", userId));
        counts.put("user_value_answers", execute("DELETE FROM user_value_answers WHERE user_id = :userId", userId));
        // 본인확인 평문(이름·생년월일·전화·통신사). 보통 30일 스케줄러가 먼저 지우지만 남아 있으면 여기서 끝낸다.
        counts.put("user_identities", execute("DELETE FROM user_identities WHERE user_id = :userId", userId));
        // 알림·기기.
        counts.put("device_tokens", execute("DELETE FROM device_tokens WHERE user_id = :userId", userId));
        counts.put("notification_settings", execute("DELETE FROM notification_settings WHERE user_id = :userId", userId));
        counts.put("notification_history", execute("DELETE FROM notification_history WHERE user_id = :userId", userId));
        counts.put("push_bundles", execute("DELETE FROM push_bundles WHERE user_id = :userId", userId));
        counts.put("mid_participation_preferences", execute(
                "DELETE FROM mid_participation_preferences WHERE user_id = :userId", userId));
        return counts;
    }

    @Override
    public void markPurged(Long userId, LocalDateTime purgedAt) {
        em.createNativeQuery("UPDATE users SET purged_at = :purgedAt WHERE id = :userId")
                .setParameter("purgedAt", purgedAt)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private int execute(String sql, Long userId) {
        return em.createNativeQuery(sql).setParameter("userId", userId).executeUpdate();
    }
}
