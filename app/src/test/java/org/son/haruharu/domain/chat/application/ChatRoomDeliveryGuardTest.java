package org.son.haruharu.domain.chat.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ChatRoomDeliveryGuardTest {

    @Mock ChatDeliveryService chatDeliveryService;

    @Test
    @DisplayName("방 활성 guard 는 도착 시점의 방 활성 여부로 통과를 정한다")
    void givenActiveRoomGuard_whenAllows_thenFollowsRoomActivity() {
        // given
        ChatRoomDeliveryGuard guard = new ChatRoomDeliveryGuard(chatDeliveryService);
        given(chatDeliveryService.isActive(12L)).willReturn(true);
        given(chatDeliveryService.isActive(13L)).willReturn(false);

        // when & then
        assertThat(guard.allows(ChatRoomDeliveryGuard.activeRoom(12L))).isTrue();
        assertThat(guard.allows(ChatRoomDeliveryGuard.activeRoom(13L))).isFalse();
    }

    @Test
    @DisplayName("모르는 guard 는 막지 않는다(다른 버전 인스턴스가 추가한 조건 — guard 도입 전과 같은 동작)")
    void givenUnknownGuard_whenAllows_thenPermitsWithoutLookup() {
        // given
        ChatRoomDeliveryGuard guard = new ChatRoomDeliveryGuard(chatDeliveryService);

        // when & then
        assertThat(guard.allows("some-future-condition:1")).isTrue();
        then(chatDeliveryService).shouldHaveNoInteractions();
    }
}
