package org.son.haruharu.domain.notification.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.auth.implement.RefreshTokenReader;
import org.son.haruharu.domain.notification.implement.NotificationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WinbackNotifierTest {

    @Mock RefreshTokenReader refreshTokenReader;
    @Mock PushSendService pushSendService;

    WinbackNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new WinbackNotifier(refreshTokenReader, pushSendService);
    }

    @Test
    @DisplayName("마지막 접속이 3일 전 하루 구간인 유저에게 WINBACK을 발송한다")
    void givenInactive3Days_whenNotify_thenSendsWinback() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 7, 20, 0);
        LocalDateTime from = LocalDate.of(2026, 7, 4).atStartOfDay();
        LocalDateTime to = LocalDate.of(2026, 7, 5).atStartOfDay();
        given(refreshTokenReader.findInactiveUserIds(from, to)).willReturn(List.of(1L, 2L));

        int notified = notifier.notifyInactiveUsers(now);

        assertThat(notified).isEqualTo(2);
        then(pushSendService).should()
                .sendToUsers(List.of(1L, 2L), NotificationType.WINBACK, Map.of());
    }

    @Test
    @DisplayName("조회 window는 오늘 기준 정확히 3일 전 하루 구간이다(재발송 방지)")
    void givenNow_whenNotify_thenQueriesExactly3DaysAgoWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 7, 20, 0);
        given(refreshTokenReader.findInactiveUserIds(any(), any())).willReturn(List.of());

        notifier.notifyInactiveUsers(now);

        then(refreshTokenReader).should().findInactiveUserIds(
                LocalDate.of(2026, 7, 4).atStartOfDay(),
                LocalDate.of(2026, 7, 5).atStartOfDay());
    }

    @Test
    @DisplayName("대상 잠수 유저가 없으면 발송하지 않는다")
    void givenNoInactiveUser_whenNotify_thenSkips() {
        given(refreshTokenReader.findInactiveUserIds(any(), any())).willReturn(List.of());

        int notified = notifier.notifyInactiveUsers(LocalDateTime.of(2026, 7, 7, 20, 0));

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(any(), any(), any());
    }
}
