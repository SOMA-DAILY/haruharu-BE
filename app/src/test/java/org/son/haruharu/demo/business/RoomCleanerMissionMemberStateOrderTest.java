package org.son.haruharu.demo.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.son.haruharu.devsupport.DevRoomCleaner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class RoomCleanerMissionMemberStateOrderTest {

    @Test
    @DisplayName("demo cleaner는 mission_member_states를 room_missions보다 먼저 삭제한다")
    void givenDemoRoom_whenClean_thenDeletesMemberStatesBeforeRoomMissions() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        new DemoRoomCleaner(jdbcTemplate).deleteRoomCascade(1L);

        assertRoomLockComesFirst(jdbcTemplate);
        assertMissionFkOrder(jdbcTemplate);
        assertVideoUploadFkOrder(jdbcTemplate);
    }

    @Test
    @DisplayName("dev cleaner는 mission_member_states를 room_missions보다 먼저 삭제한다")
    void givenDevRoom_whenClean_thenDeletesMemberStatesBeforeRoomMissions() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        new DevRoomCleaner(jdbcTemplate).deleteRoomCascade(1L);

        assertRoomLockComesFirst(jdbcTemplate);
        assertMissionFkOrder(jdbcTemplate);
        assertVideoUploadFkOrder(jdbcTemplate);
    }

    private void assertRoomLockComesFirst(JdbcTemplate jdbcTemplate) {
        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).queryForObject(
                "SELECT id FROM rooms WHERE id = ? FOR UPDATE", Long.class, 1L);
        order.verify(jdbcTemplate).update(contains("DELETE FROM chat_messages"), eq(1L), eq(1L));
    }

    private void assertMissionFkOrder(JdbcTemplate jdbcTemplate) {
        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).update(
                "DELETE FROM mission_member_states WHERE room_mission_id IN (SELECT id FROM room_missions WHERE room_id = ?)",
                1L);
        order.verify(jdbcTemplate).update("DELETE FROM room_missions WHERE room_id = ?", 1L);
    }

    private void assertVideoUploadFkOrder(JdbcTemplate jdbcTemplate) {
        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).update("DELETE FROM video_upload_sessions WHERE room_id = ?", 1L);
        order.verify(jdbcTemplate).update(contains("DELETE FROM video_posts"), eq(1L));
        order.verify(jdbcTemplate).update("DELETE FROM room_members WHERE room_id = ?", 1L);
    }
}
