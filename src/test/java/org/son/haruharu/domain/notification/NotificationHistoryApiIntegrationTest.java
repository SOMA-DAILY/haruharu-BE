package org.son.haruharu.domain.notification;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.notification.business.PushSendService;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 트리거(발송) → 실제 DB 저장 → 조회 → 읽음 왕복을 Testcontainers PostgreSQL로 검증한다.
 * 테스트 간 데이터 격리를 위해 각 테스트가 서로 다른 userId를 사용한다(클래스 @Transactional 없음).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationHistoryApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired PushSendService pushSendService;

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("inboxVisible 알림을 발송하면 알림함에 저장되어 GET으로 조회된다")
    void givenInboxVisibleSent_whenGet_thenStoredAndReturned() throws Exception {
        Long userId = 8001L;
        pushSendService.sendToUsers(List.of(userId), NotificationType.ROOM_MATCHED, Map.of("roomId", "7"));

        mockMvc.perform(get("/api/v1/users/me/notifications").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications[0].type").value("ROOM_MATCHED"))
                .andExpect(jsonPath("$.data.notifications[0].title").value(NotificationType.ROOM_MATCHED.title()))
                .andExpect(jsonPath("$.data.notifications[0].payload.roomId").value("7"))
                .andExpect(jsonPath("$.data.notifications[0].is_read").value(false))
                .andExpect(jsonPath("$.data.unread_count").value(1));
    }

    @Test
    @DisplayName("inboxVisible=false 알림(SLOT_START)은 알림함에 저장되지 않는다")
    void givenNonInboxSent_whenGet_thenNotStored() throws Exception {
        Long userId = 8002L;
        pushSendService.sendToUsers(List.of(userId), NotificationType.SLOT_START, Map.of("roomId", "7"));

        mockMvc.perform(get("/api/v1/users/me/notifications").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications").isEmpty())
                .andExpect(jsonPath("$.data.unread_count").value(0));
    }

    @Test
    @DisplayName("읽음 처리하면 unread_count가 0이 되고 is_read=true가 유지된다")
    void givenMarkRead_whenGet_thenUnreadZero() throws Exception {
        Long userId = 8003L;
        pushSendService.sendToUsers(List.of(userId), NotificationType.REACTION_RECEIVED, Map.of("videoPostId", "5"));

        mockMvc.perform(patch("/api/v1/users/me/notifications/read").with(authentication(auth(userId))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/notifications").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications[0].is_read").value(true))
                .andExpect(jsonPath("$.data.unread_count").value(0));
    }

    @Test
    @DisplayName("다른 유저의 알림은 조회되지 않는다(본인 격리)")
    void givenOtherUserNotification_whenGet_thenNotVisible() throws Exception {
        pushSendService.sendToUsers(List.of(8004L), NotificationType.ROOM_MATCHED, Map.of());

        mockMvc.perform(get("/api/v1/users/me/notifications").with(authentication(auth(8005L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications").isEmpty());
    }
}
