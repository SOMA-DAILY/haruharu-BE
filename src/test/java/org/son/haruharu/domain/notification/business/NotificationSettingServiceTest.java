package org.son.haruharu.domain.notification.business;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.business.command.UpdateNotificationSettingCommand;
import org.son.haruharu.domain.notification.business.dto.NotificationSettingResponse;
import org.son.haruharu.domain.notification.implement.NotificationSetting;
import org.son.haruharu.domain.notification.implement.NotificationSettingReader;
import org.son.haruharu.domain.notification.implement.NotificationSettingWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    @Mock NotificationSettingReader notificationSettingReader;
    @Mock NotificationSettingWriter notificationSettingWriter;

    NotificationSettingService service;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        service = new NotificationSettingService(notificationSettingReader, notificationSettingWriter);
    }

    @Test
    @DisplayName("저장된 설정이 없으면 기본값 on으로 응답한다")
    void givenNoSetting_whenGetSetting_thenReturnsDefaultOn() {
        given(notificationSettingReader.findByUserId(userId)).willReturn(Optional.empty());

        NotificationSettingResponse result = service.getSetting(userId);

        assertThat(result.pushEnabled()).isTrue();
    }

    @Test
    @DisplayName("off로 저장된 설정이 있으면 off로 응답한다")
    void givenDisabledSetting_whenGetSetting_thenReturnsOff() {
        given(notificationSettingReader.findByUserId(userId))
                .willReturn(Optional.of(new NotificationSetting(5L, userId, false)));

        NotificationSettingResponse result = service.getSetting(userId);

        assertThat(result.pushEnabled()).isFalse();
    }

    @Test
    @DisplayName("설정이 없던 유저가 off로 변경하면 신규 행으로 저장된다")
    void givenNoSetting_whenUpdateToOff_thenSavesNewRow() {
        given(notificationSettingReader.findByUserId(userId)).willReturn(Optional.empty());
        given(notificationSettingWriter.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        NotificationSettingResponse result =
                service.updateSetting(userId, new UpdateNotificationSettingCommand(false));

        assertThat(result.pushEnabled()).isFalse();
        ArgumentCaptor<NotificationSetting> captor = ArgumentCaptor.forClass(NotificationSetting.class);
        then(notificationSettingWriter).should().save(captor.capture());
        assertThat(captor.getValue().id()).isNull();
        assertThat(captor.getValue().userId()).isEqualTo(userId);
        assertThat(captor.getValue().pushEnabled()).isFalse();
    }

    @Test
    @DisplayName("기존 설정이 있으면 같은 id로 갱신한다(멱등)")
    void givenExistingSetting_whenUpdate_thenUpdatesSameId() {
        given(notificationSettingReader.findByUserId(userId))
                .willReturn(Optional.of(new NotificationSetting(5L, userId, false)));
        given(notificationSettingWriter.save(org.mockito.ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        NotificationSettingResponse result =
                service.updateSetting(userId, new UpdateNotificationSettingCommand(true));

        assertThat(result.pushEnabled()).isTrue();
        ArgumentCaptor<NotificationSetting> captor = ArgumentCaptor.forClass(NotificationSetting.class);
        then(notificationSettingWriter).should().save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(5L);
        assertThat(captor.getValue().pushEnabled()).isTrue();
    }
}
