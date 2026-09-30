package org.son.haruharu.devsupport;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * [dev·local 전용, #180] 테스트 방 파생 데이터 일괄 삭제.
 * 반복 테스트를 위한 하드 삭제라 도메인 Repository(soft-delete 계약)에 dev 전용 삭제
 * 메서드를 추가하지 않고 SQL로 직접 정리한다. 운영 코드 경로에서는 절대 사용하지 않는다.
 */
@Profile({"local", "dev"})
@Component
public class DevRoomCleaner {

    private final JdbcTemplate jdbcTemplate;

    public DevRoomCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void deleteRoomCascade(Long roomId) {
        jdbcTemplate.queryForObject(
                "SELECT id FROM rooms WHERE id = ? FOR UPDATE", Long.class, roomId);
        // 채팅방(DIRECT=매치결과 경유, GROUP=room_id 직결) → 멤버·메시지. 두 유형을 모두 chat_rooms.id로 모아 정리한다.
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
        jdbcTemplate.update("""
                DELETE FROM mission_assignment_incidents WHERE room_mission_slot_id IN (
                    SELECT id FROM room_mission_slots WHERE room_id = ?)""", roomId);
        jdbcTemplate.update(
                "DELETE FROM mission_member_states WHERE room_mission_id IN (SELECT id FROM room_missions WHERE room_id = ?)",
                roomId);
        jdbcTemplate.update("DELETE FROM room_missions WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM room_mission_slots WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM room_mission_settings WHERE room_id = ?", roomId);
        // 영상 업로드 세션은 게시물·멤버를 모두 참조하므로 가장 먼저 지운다.
        jdbcTemplate.update("DELETE FROM video_upload_sessions WHERE room_id = ?", roomId);
        // 영상(리액션 → 포스트)
        jdbcTemplate.update("""
                DELETE FROM video_reactions WHERE video_post_id IN (
                    SELECT v.id FROM video_posts v
                    JOIN room_members m ON m.id = v.room_member_id
                    WHERE m.room_id = ?)""", roomId);
        jdbcTemplate.update("""
                DELETE FROM video_posts WHERE room_member_id IN (
                    SELECT id FROM room_members WHERE room_id = ?)""", roomId);
        // 멤버 → 방
        jdbcTemplate.update("DELETE FROM room_members WHERE room_id = ?", roomId);
        jdbcTemplate.update("DELETE FROM rooms WHERE id = ?", roomId);
    }
}
