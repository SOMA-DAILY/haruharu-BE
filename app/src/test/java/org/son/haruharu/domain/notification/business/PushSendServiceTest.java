package org.son.haruharu.domain.notification.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.implement.DeviceToken;
import org.son.haruharu.domain.notification.implement.DeviceTokenReader;
import org.son.haruharu.domain.notification.implement.DeviceTokenWriter;
import org.son.haruharu.domain.notification.implement.NotificationSettingReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.infrastructure.fcm.PushMessage;
import org.son.haruharu.infrastructure.fcm.PushPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PushSendServiceTest {

    @Mock DeviceTokenReader deviceTokenReader;
    @Mock DeviceTokenWriter deviceTokenWriter;
    @Mock NotificationSettingReader notificationSettingReader;
    @Mock NotificationHistoryService notificationHistoryService;
    @Mock PushPort pushPort;

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final QuietHoursProperties QUIET_00_08 = new QuietHoursProperties(true, LocalTime.MIDNIGHT, LocalTime.of(8, 0));

    PushSendService service;

    @BeforeEach
    void setUp() {
        // 기본은 주간(12:00 KST) — 야간 차단 시나리오는 별도 서비스 인스턴스로 만든다
        service = serviceAt(LocalTime.NOON, QUIET_00_08);
        // 기본: 활동 알림을 끈 유저 없음(전원 수신), 혜택 알림을 켠 유저 없음(전원 미수신)
        lenient().when(notificationSettingReader.findActivityDisabledUserIds(anyList())).thenReturn(List.of());
        lenient().when(notificationSettingReader.findMarketingEnabledUserIds(anyList())).thenReturn(List.of());
    }

    @Test
    @DisplayName("대상 유저들의 모든 등록 토큰으로 타입 문구·데이터를 담아 발송한다")
    void givenRegisteredTokens_whenSend_thenSendsToAllTokens() {
        given(deviceTokenReader.findAllByUserIds(List.of(1L, 2L))).willReturn(List.of(
                new DeviceToken(10L, 1L, "token-1"),
                new DeviceToken(11L, 1L, "token-2"),
                new DeviceToken(12L, 2L, "token-3")));
        given(pushPort.send(any())).willReturn(List.of());

        service.sendToUsers(List.of(1L, 2L), NotificationType.ROOM_MATCHED, Map.of("roomId", "7"));

        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        then(pushPort).should().send(captor.capture());
        PushMessage message = captor.getValue();
        assertThat(message.tokens()).containsExactlyInAnyOrder("token-1", "token-2", "token-3");
        assertThat(message.title()).isEqualTo(NotificationType.ROOM_MATCHED.title());
        assertThat(message.body()).isEqualTo(NotificationType.ROOM_MATCHED.body());
        assertThat(message.data())
                .containsEntry("type", "ROOM_MATCHED")
                .containsEntry("roomId", "7");
        then(deviceTokenWriter).should(never()).deleteAllByTokens(anyCollection());
    }

    @Test
    @DisplayName("bodyArgs를 넘기면 문구의 %d가 실제 값으로 치환된다")
    void givenBodyArgs_whenSend_thenFormatsPlaceholder() {
        given(deviceTokenReader.findAllByUserIds(List.of(1L)))
                .willReturn(List.of(new DeviceToken(10L, 1L, "token-1")));
        given(pushPort.send(any())).willReturn(List.of());

        service.sendToUsers(List.of(1L), NotificationType.COIN_REFUNDED, Map.of(), 5L);

        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        then(pushPort).should().send(captor.capture());
        assertThat(captor.getValue().title())
                .contains("5")
                .doesNotContain("%d");
    }

    @Test
    @DisplayName("FCM이 UNREGISTERED로 응답한 토큰은 삭제하고 흐름은 계속된다")
    void givenUnregisteredTokens_whenSend_thenDeletesThem() {
        given(deviceTokenReader.findAllByUserIds(List.of(1L))).willReturn(List.of(
                new DeviceToken(10L, 1L, "alive"),
                new DeviceToken(11L, 1L, "dead")));
        given(pushPort.send(any())).willReturn(List.of("dead"));

        service.sendToUsers(List.of(1L), NotificationType.ROOM_MATCHED, Map.of());

        then(deviceTokenWriter).should().deleteAllByTokens(List.of("dead"));
    }

    @Test
    @DisplayName("등록 토큰이 없으면 발송하지 않는다")
    void givenNoTokens_whenSend_thenSkips() {
        given(deviceTokenReader.findAllByUserIds(List.of(1L))).willReturn(List.of());

        service.sendToUsers(List.of(1L), NotificationType.ROOM_MATCHED, Map.of());

        then(pushPort).should(never()).send(any());
    }

    @Test
    @DisplayName("활동 알림을 끈 유저는 발송 대상에서 제외된다(opt-out)")
    void givenActivityDisabledUser_whenSendActivity_thenExcluded() {
        given(notificationSettingReader.findActivityDisabledUserIds(List.of(1L, 2L))).willReturn(List.of(2L));
        given(deviceTokenReader.findAllByUserIds(List.of(1L)))
                .willReturn(List.of(new DeviceToken(10L, 1L, "token-1")));
        given(pushPort.send(any())).willReturn(List.of());

        service.sendToUsers(List.of(1L, 2L), NotificationType.ROOM_MATCHED, Map.of());

        then(deviceTokenReader).should().findAllByUserIds(List.of(1L));
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        then(pushPort).should().send(captor.capture());
        assertThat(captor.getValue().tokens()).containsExactly("token-1");
    }

    @Test
    @DisplayName("전원이 활동 알림을 껐으면 토큰 조회·발송을 하지 않는다")
    void givenAllActivityDisabled_whenSendActivity_thenSkips() {
        given(notificationSettingReader.findActivityDisabledUserIds(List.of(1L))).willReturn(List.of(1L));

        service.sendToUsers(List.of(1L), NotificationType.ROOM_MATCHED, Map.of());

        then(deviceTokenReader).should(never()).findAllByUserIds(anyList());
        then(pushPort).should(never()).send(any());
    }

    @Test
    @DisplayName("혜택 알림은 명시적으로 켠 유저에게만 발송한다(opt-in, 기본 미수신)")
    void givenMarketingType_whenSend_thenOnlyOptedInUsersReceive() {
        given(notificationSettingReader.findMarketingEnabledUserIds(List.of(1L, 2L))).willReturn(List.of(2L));
        given(deviceTokenReader.findAllByUserIds(List.of(2L)))
                .willReturn(List.of(new DeviceToken(20L, 2L, "token-2")));
        given(pushPort.send(any())).willReturn(List.of());

        service.sendToUsers(List.of(1L, 2L), NotificationType.WINBACK, Map.of());

        then(deviceTokenReader).should().findAllByUserIds(List.of(2L));
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        then(pushPort).should().send(captor.capture());
        assertThat(captor.getValue().tokens()).containsExactly("token-2");
    }

    @Test
    @DisplayName("혜택 알림을 켠 유저가 없으면 발송하지 않는다(기본 off)")
    void givenNoMarketingOptIn_whenSend_thenSkips() {
        service.sendToUsers(List.of(1L, 2L), NotificationType.WINBACK, Map.of());

        then(deviceTokenReader).should(never()).findAllByUserIds(anyList());
        then(pushPort).should(never()).send(any());
    }

    @Test
    @DisplayName("거래 알림(TRANSACTIONAL)은 수신 설정과 무관하게 항상 발송한다")
    void givenTransactionalType_whenSend_thenAlwaysSent() {
        given(deviceTokenReader.findAllByUserIds(List.of(1L)))
                .willReturn(List.of(new DeviceToken(10L, 1L, "token-1")));
        given(pushPort.send(any())).willReturn(List.of());

        service.sendToUsers(List.of(1L), NotificationType.PAYMENT_COMPLETED, Map.of(), 3L);

        then(notificationSettingReader).should(never()).findActivityDisabledUserIds(anyList());
        then(notificationSettingReader).should(never()).findMarketingEnabledUserIds(anyList());
        then(pushPort).should().send(any());
    }

    @Test
    @DisplayName("발송 어댑터가 예외를 던져도 호출자에게 전파하지 않는다")
    void givenPortThrows_whenSend_thenDoesNotPropagate() {
        given(deviceTokenReader.findAllByUserIds(List.of(1L)))
                .willReturn(List.of(new DeviceToken(10L, 1L, "token-1")));
        given(pushPort.send(any())).willThrow(new RuntimeException("fcm down"));

        assertThatCode(() -> service.sendToUsers(List.of(1L), NotificationType.ROOM_MATCHED, Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("inboxVisible 타입은 대상 유저 전원의 알림함에 저장한다")
    void givenInboxVisibleType_whenSend_thenSavesHistory() {
        given(deviceTokenReader.findAllByUserIds(any())).willReturn(List.of());

        service.sendToUsers(List.of(1L, 2L), NotificationType.ROOM_MATCHED, Map.of("roomId", "7"));

        then(notificationHistoryService).should().saveAll(
                eq(List.of(1L, 2L)), eq(NotificationType.ROOM_MATCHED), any(), any(), eq(Map.of("roomId", "7")));
    }

    @Test
    @DisplayName("inboxVisible=false 타입은 알림함에 저장하지 않는다")
    void givenNonInboxType_whenSend_thenNoHistory() {
        given(deviceTokenReader.findAllByUserIds(any())).willReturn(List.of());

        service.sendToUsers(List.of(1L), NotificationType.SLOT_START, Map.of());

        then(notificationHistoryService).should(never()).saveAll(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("활동 알림을 껐어도 알림함에는 저장한다(설정 무관)")
    void givenActivityDisabled_whenSend_thenStillSavesHistory() {
        given(notificationSettingReader.findActivityDisabledUserIds(List.of(1L))).willReturn(List.of(1L));

        service.sendToUsers(List.of(1L), NotificationType.ROOM_MATCHED, Map.of());

        then(notificationHistoryService).should().saveAll(eq(List.of(1L)), any(), any(), any(), any());
        then(pushPort).should(never()).send(any());
    }

    // ===================== 야간 차단(#429) =====================

    private PushSendService serviceAt(LocalTime kstTime, QuietHoursProperties quietHours) {
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 9, 16, kstTime.getHour(), kstTime.getMinute())
                .atZone(SEOUL).toInstant(), SEOUL);
        return new PushSendService(deviceTokenReader, deviceTokenWriter,
                notificationSettingReader, notificationHistoryService, pushPort, quietHours, clock);
    }

    @Test
    @DisplayName("야간(03:00 KST)에는 야간 예외가 아닌 타입은 알림함에는 저장하되 푸시는 보내지 않는다")
    void givenQuietHours_whenSend_thenSavesInboxButSkipsPush() {
        PushSendService night = serviceAt(LocalTime.of(3, 0), QUIET_00_08);

        night.sendToUsers(List.of(1L), NotificationType.FINAL_SELECTION_OPEN, Map.of());
        night.sendToUsers(List.of(1L), NotificationType.COIN_REFUNDED, Map.of(), 5L); // TRANSACTIONAL 도 차단

        then(notificationHistoryService).should().saveAll(eq(List.of(1L)), eq(NotificationType.FINAL_SELECTION_OPEN), any(), any(), any());
        then(notificationHistoryService).should().saveAll(eq(List.of(1L)), eq(NotificationType.COIN_REFUNDED), any(), any(), any());
        then(deviceTokenReader).should(never()).findAllByUserIds(anyList());
        then(pushPort).should(never()).send(any());
    }

    // ===================== 야간 차단 예외(#446) =====================

    @Test
    @DisplayName("야간 예외 타입은 채팅·영상 반응·영상 메시지·영상 업로드·질문 도착·답변 도착 6종뿐이다")
    void givenNotificationTypes_whenInspectQuietHoursExempt_thenOnlyUserInteractionTypes() {
        assertThat(java.util.Arrays.stream(NotificationType.values()).filter(NotificationType::quietHoursExempt))
                .containsExactlyInAnyOrder(
                        NotificationType.CHAT, NotificationType.REACTION_RECEIVED, NotificationType.VIDEO_REPLY,
                        NotificationType.VIDEO_UPLOADED, NotificationType.QUESTION_RECEIVED,
                        NotificationType.QUESTION_ANSWERED);
    }

    @Test
    @DisplayName("야간(03:00 KST)에도 야간 예외 타입은 푸시를 발송한다")
    void givenQuietHoursAndExemptType_whenSend_thenSendsPush() {
        given(deviceTokenReader.findAllByUserIds(List.of(1L)))
                .willReturn(List.of(new DeviceToken(10L, 1L, "token-1")));
        given(pushPort.send(any())).willReturn(List.of());
        PushSendService night = serviceAt(LocalTime.of(3, 0), QUIET_00_08);

        night.sendToUsers(List.of(1L), NotificationType.CHAT, Map.of(), "민준");
        night.sendToUsers(List.of(1L), NotificationType.REACTION_RECEIVED, Map.of(), "노을", "마음에 들어해요", "❤️");
        night.sendToUsers(List.of(1L), NotificationType.VIDEO_REPLY, Map.of(), "노을");
        night.sendToUsers(List.of(1L), NotificationType.VIDEO_UPLOADED, Map.of(), "하늘");
        night.sendToUsers(List.of(1L), NotificationType.QUESTION_RECEIVED, Map.of());
        night.sendToUsers(List.of(1L), NotificationType.QUESTION_ANSWERED, Map.of());

        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        then(pushPort).should(org.mockito.Mockito.times(6)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(m -> m.data().get("type")).containsExactly(
                "CHAT", "REACTION_RECEIVED", "VIDEO_REPLY", "VIDEO_UPLOADED", "QUESTION_RECEIVED", "QUESTION_ANSWERED");
    }

    @Test
    @DisplayName("야간 예외 타입이라도 활동 알림을 끈 유저에게는 푸시를 보내지 않는다")
    void givenQuietHoursExemptTypeAndActivityDisabled_whenSend_thenSkipsPush() {
        given(notificationSettingReader.findActivityDisabledUserIds(List.of(1L))).willReturn(List.of(1L));

        serviceAt(LocalTime.of(3, 0), QUIET_00_08).sendToUsers(List.of(1L), NotificationType.CHAT, Map.of(), "민준");

        then(pushPort).should(never()).send(any());
    }

    @Test
    @DisplayName("차단 구간 경계: 00:00은 차단, 08:00과 23:59는 발송한다")
    void givenBoundaryTimes_whenSend_thenOnlyInsideWindowIsBlocked() {
        given(deviceTokenReader.findAllByUserIds(List.of(1L)))
                .willReturn(List.of(new DeviceToken(10L, 1L, "token-1")));
        given(pushPort.send(any())).willReturn(List.of());

        serviceAt(LocalTime.MIDNIGHT, QUIET_00_08).sendToUsers(List.of(1L), NotificationType.SLOT_START, Map.of());
        then(pushPort).should(never()).send(any());

        serviceAt(LocalTime.of(8, 0), QUIET_00_08).sendToUsers(List.of(1L), NotificationType.SLOT_START, Map.of());
        serviceAt(LocalTime.of(23, 59), QUIET_00_08).sendToUsers(List.of(1L), NotificationType.SLOT_START, Map.of());
        then(pushPort).should(org.mockito.Mockito.times(2)).send(any());
    }

    @Test
    @DisplayName("quiet-hours.enabled=false 면 야간에도 발송한다")
    void givenQuietHoursDisabled_whenSendAtNight_thenSends() {
        given(deviceTokenReader.findAllByUserIds(List.of(1L)))
                .willReturn(List.of(new DeviceToken(10L, 1L, "token-1")));
        given(pushPort.send(any())).willReturn(List.of());
        QuietHoursProperties disabled = new QuietHoursProperties(false, LocalTime.MIDNIGHT, LocalTime.of(8, 0));

        serviceAt(LocalTime.of(3, 0), disabled).sendToUsers(List.of(1L), NotificationType.SLOT_START, Map.of());

        then(pushPort).should().send(any());
    }

    @Test
    @DisplayName("자정을 넘는 구간(23:00~07:00)도 올바르게 판정한다")
    void givenOvernightWindow_whenIsQuiet_thenWrapsMidnight() {
        QuietHoursProperties overnight = new QuietHoursProperties(true, LocalTime.of(23, 0), LocalTime.of(7, 0));

        assertThat(overnight.isQuiet(LocalTime.of(23, 0))).isTrue();
        assertThat(overnight.isQuiet(LocalTime.of(2, 30))).isTrue();
        assertThat(overnight.isQuiet(LocalTime.of(7, 0))).isFalse();
        assertThat(overnight.isQuiet(LocalTime.of(22, 59))).isFalse();
    }

    @Test
    @DisplayName("start == end 는 0폭 구간이라 어느 시각에도 차단하지 않는다")
    void givenEmptyWindow_whenIsQuiet_thenNeverQuiet() {
        QuietHoursProperties empty = new QuietHoursProperties(true, LocalTime.of(8, 0), LocalTime.of(8, 0));

        assertThat(empty.isQuiet(LocalTime.of(8, 0))).isFalse();
        assertThat(empty.isQuiet(LocalTime.of(3, 0))).isFalse();
    }
}
