package org.son.haruharu.domain.notification.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.business.dto.NotificationsResponse;
import org.son.haruharu.domain.notification.implement.NotificationHistory;
import org.son.haruharu.domain.notification.implement.NotificationHistoryReader;
import org.son.haruharu.domain.notification.implement.NotificationHistoryWriter;
import org.son.haruharu.domain.notification.implement.NotificationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationHistoryServiceTest {

    @Mock NotificationHistoryReader notificationHistoryReader;
    @Mock NotificationHistoryWriter notificationHistoryWriter;

    NotificationHistoryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationHistoryService(notificationHistoryReader, notificationHistoryWriter);
    }

    private NotificationHistory hist(Long id) {
        return new NotificationHistory(id, 1L, NotificationType.ROOM_MATCHED,
                "제목", "내용", Map.of("roomId", "7"), false, LocalDateTime.of(2026, 7, 7, 9, 0));
    }

    @Test
    @DisplayName("본인 알림을 페이지와 안읽음 수와 함께 반환한다")
    void givenNotifications_whenGet_thenReturnsPageWithUnread() {
        given(notificationHistoryReader.getPage(1L, null, 20)).willReturn(List.of(hist(3L), hist(2L)));
        given(notificationHistoryReader.countUnread(1L)).willReturn(5L);

        NotificationsResponse response = service.getNotifications(1L, null, 20);

        assertThat(response.notifications()).hasSize(2);
        assertThat(response.unreadCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("size만큼 꽉 차면 마지막 id를 nextCursor로 반환한다")
    void givenFullPage_whenGet_thenNextCursorIsLastId() {
        given(notificationHistoryReader.getPage(1L, null, 2)).willReturn(List.of(hist(3L), hist(2L)));
        given(notificationHistoryReader.countUnread(1L)).willReturn(0L);

        NotificationsResponse response = service.getNotifications(1L, null, 2);

        assertThat(response.nextCursor()).isEqualTo(2L);
    }

    @Test
    @DisplayName("size보다 적게 오면 nextCursor는 null이다(마지막 페이지)")
    void givenPartialPage_whenGet_thenNoNextCursor() {
        given(notificationHistoryReader.getPage(1L, null, 20)).willReturn(List.of(hist(3L)));
        given(notificationHistoryReader.countUnread(1L)).willReturn(0L);

        NotificationsResponse response = service.getNotifications(1L, null, 20);

        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("전체 읽음 처리를 Writer에 위임한다")
    void whenMarkAllRead_thenDelegates() {
        service.markAllRead(1L);

        then(notificationHistoryWriter).should().markAllRead(1L);
    }
}
