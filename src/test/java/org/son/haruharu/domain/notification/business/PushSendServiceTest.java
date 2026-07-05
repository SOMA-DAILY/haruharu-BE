package org.son.haruharu.domain.notification.business;

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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PushSendServiceTest {

    @Mock DeviceTokenReader deviceTokenReader;
    @Mock DeviceTokenWriter deviceTokenWriter;
    @Mock NotificationSettingReader notificationSettingReader;
    @Mock PushPort pushPort;

    PushSendService service;

    @BeforeEach
    void setUp() {
        service = new PushSendService(deviceTokenReader, deviceTokenWriter, notificationSettingReader, pushPort);
        lenient().when(notificationSettingReader.findPushDisabledUserIds(anyList())).thenReturn(List.of());
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
    @DisplayName("푸시 수신을 끈 유저는 발송 대상에서 제외된다")
    void givenDisabledUser_whenSend_thenExcluded() {
        given(notificationSettingReader.findPushDisabledUserIds(List.of(1L, 2L))).willReturn(List.of(2L));
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
    @DisplayName("전원이 푸시 수신을 껐으면 토큰 조회·발송을 하지 않는다")
    void givenAllDisabled_whenSend_thenSkips() {
        given(notificationSettingReader.findPushDisabledUserIds(List.of(1L))).willReturn(List.of(1L));

        service.sendToUsers(List.of(1L), NotificationType.ROOM_MATCHED, Map.of());

        then(deviceTokenReader).should(never()).findAllByUserIds(anyList());
        then(pushPort).should(never()).send(any());
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
}
