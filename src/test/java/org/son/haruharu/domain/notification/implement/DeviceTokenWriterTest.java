package org.son.haruharu.domain.notification.implement;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DeviceTokenWriterTest {

    @Mock DeviceTokenRepository deviceTokenRepository;

    DeviceTokenWriter writer;

    @BeforeEach
    void setUp() {
        writer = new DeviceTokenWriter(deviceTokenRepository);
    }

    @Test
    @DisplayName("처음 보는 토큰이면 새로 저장한다")
    void givenNewToken_whenUpsert_thenSaves() {
        given(deviceTokenRepository.findByToken("fcm-abc")).willReturn(Optional.empty());
        given(deviceTokenRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        writer.upsert(1L, "fcm-abc");

        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        then(deviceTokenRepository).should().save(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(1L);
        assertThat(captor.getValue().token()).isEqualTo("fcm-abc");
    }

    @Test
    @DisplayName("같은 유저가 같은 토큰을 재등록하면 중복 저장하지 않는다")
    void givenSameUserSameToken_whenUpsert_thenNoDuplicateSave() {
        given(deviceTokenRepository.findByToken("fcm-abc"))
                .willReturn(Optional.of(new DeviceToken(10L, 1L, "fcm-abc")));

        writer.upsert(1L, "fcm-abc");

        then(deviceTokenRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("다른 유저가 이미 등록된 토큰을 등록하면 소유자를 이전한다")
    void givenTokenOwnedByOtherUser_whenUpsert_thenReassignsOwner() {
        given(deviceTokenRepository.findByToken("fcm-abc"))
                .willReturn(Optional.of(new DeviceToken(10L, 1L, "fcm-abc")));
        given(deviceTokenRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        writer.upsert(2L, "fcm-abc");

        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        then(deviceTokenRepository).should().save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(10L);
        assertThat(captor.getValue().userId()).isEqualTo(2L);
    }
}
