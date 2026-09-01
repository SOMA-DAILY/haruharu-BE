package org.son.haruharu.demo.business;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 데모 방 하드 삭제(#352 PR2). 데모 재생성 시 이전 방과 파생 데이터를 물리 삭제해 재생성 충돌(soft-delete 잔재가
 * 점유한 unique 슬롯)과 누적을 막는다. 전역 @SoftDelete 를 우회해야 해서 도메인 Repository 대신 원시 SQL 로 정리한다.
 * <p>
 * devsupport 의 DevRoomCleaner 와 같은 성격이지만 그쪽은 {@code @Profile("local","dev")} 라 prod 에서 빈이 없다.
 * 심사용 데모는 prod 에서 리셋해야 하므로 프로파일 제한 없는 데모 전용 클리너를 둔다. DevRoomCleaner 와 달리
 * {@code room_missions}(#341)까지 지운다 — 그쪽엔 누락돼 있다.
 */
@Component
public class DemoRoomCleaner {

    private final JdbcTemplate jdbcTemplate;

    public DemoRoomCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void deleteRoomCascade(Long roomId) {
        // 채팅방(DIRECT=매치결과 경유, GROUP=room_id 직결) → 멤버·메시지. 두 유형 chat_rooms.id를 모아 정리한다.
        String chatRoomIds = """
                SELECT id FROM chat_rooms WHERE room_id = ?
                UNION
                SELECT cr.id FROM chat_rooms cr
                    JOIN match_results mr ON mr.id = cr.match_result_id
                    JOIN room_members m ON m.id IN (mr.member_a_id, mr.member_b_id)
                    WHERE m.room_id = ?""";
        jdbcTemplate.update("DELETE FROM chat_messages WHERE chat_room_id IN (" + chatRoomIds + ")", roomId, roomId);
        jdbcTemplate.update("DELETE FROM chat_room_members WHERE chat_room_id IN (" + chatRoomIds + ")", roomId, roomId);
        jdbcTemplate.update("DELETE FROM chat_rooms WHERE room_id = ?", roomId);
        jdbcTemplate.update("""
                DELETE FROM chat_rooms WHERE match_result_id IN (
                    SELECT mr.id FROM match_results mr
                    JOIN room_members m ON m.id IN (mr.member_a_id, mr.member_b_id)
                    WHERE m.room_id = ?)""", roomId);
        // 매치결과·최종선택
        jdbcTemplate.update("""
                DELETE FROM match_results WHERE member_a_id IN (
                    SELECT id FROM room_members WHERE room_id = ?)""", roomId);
        jdbcTemplate.update("""
                DELETE FROM final_selections WHERE room_member_id IN (
                    SELECT id FROM room_members WHERE room_id = ?)""", roomId);
        // 미션 배정 incident → 인스턴스 → 슬롯 (FK 역순)
        jdbcTemplate.update("""
                DELETE FROM mission_assignment_incidents WHERE room_mission_slot_id IN (
                    SELECT id FROM room_mission_slots WHERE room_id = ?)""", roomId);
        jdbcTemplate.update("DELETE FROM room_missions WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM room_mission_slots WHERE room_id = ?", roomId);
        // 영상(리액션 → 포스트)
        jdbcTemplate.update("""
                DELETE FROM video_reactions WHERE video_post_id IN (
                    SELECT v.id FROM video_posts v
                    JOIN room_members m ON m.id = v.room_member_id
                    WHERE m.room_id = ?)""", roomId);
        jdbcTemplate.update("""
                DELETE FROM video_posts WHERE room_member_id IN (
                    SELECT id FROM room_members WHERE room_id = ?)""", roomId);
        // 초대코드(있으면) → 멤버 → 방
        jdbcTemplate.update("DELETE FROM room_invites WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM rooms WHERE id = ?", roomId);
    }
}
