package org.son.haruharu.domain.notification.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.implement.DeviceTokenWriter;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock DeviceTokenWriter deviceTokenWriter;

    DeviceTokenService service;

    @BeforeEach
    void setUp() {
        service = new DeviceTokenService(deviceTokenWriter);
    }

    @Test
    @DisplayName("토큰 등록 요청 시 upsert로 위임한다")
    void givenToken_whenRegister_thenUpserts() {
        service.register(1L, "fcm-abc");

        then(deviceTokenWriter).should().upsert(1L, "fcm-abc");
    }

    @Test
    @DisplayName("토큰 해제 요청 시 본인 토큰만 삭제한다")
    void givenToken_whenUnregister_thenDeletesOwnToken() {
        service.unregister(1L, "fcm-abc");

        then(deviceTokenWriter).should().delete(1L, "fcm-abc");
    }
}
