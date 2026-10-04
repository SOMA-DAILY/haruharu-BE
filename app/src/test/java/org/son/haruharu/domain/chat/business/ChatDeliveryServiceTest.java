package org.son.haruharu.domain.chat.business;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ChatDeliveryServiceTest {
    @Mock ChatRoomReader reader;
    @Mock org.son.haruharu.domain.chat.implement.ChatMessageReader messages;
    @Mock Runnable delivery;

    @Test
    @DisplayName("방을 잠근 뒤 활성 상태일 때만 외부 전송을 실행한다")
    void givenActiveRoom_whenDeliver_thenRunsAfterLock() {
        given(reader.lockIfPresent(10L)).willReturn(true);
        given(reader.findById(10L)).willReturn(Optional.of(ChatRoom.createDirect(100L)));
        new ChatDeliveryService(reader, messages, org.mockito.Mockito.mock(org.son.haruharu.domain.chat.implement.ChatMissionPresenter.class)).deliverIfActive(10L, delivery);
        var order = inOrder(reader, delivery);
        order.verify(reader).lockIfPresent(10L);
        order.verify(reader).findById(10L);
        order.verify(delivery).run();
    }
    @Test
    @DisplayName("물리 삭제 또는 소프트 삭제된 방이면 외부 전송을 생략한다")
    void givenMissingOrClosedRoom_whenDeliver_thenSkips() {
        new ChatDeliveryService(reader, messages, org.mockito.Mockito.mock(org.son.haruharu.domain.chat.implement.ChatMissionPresenter.class)).deliverIfActive(10L, delivery);
        then(reader).should(never()).findById(10L);
        given(reader.lockIfPresent(10L)).willReturn(true);
        given(reader.findById(10L)).willReturn(Optional.empty());
        new ChatDeliveryService(reader, messages, org.mockito.Mockito.mock(org.son.haruharu.domain.chat.implement.ChatMissionPresenter.class)).deliverIfActive(10L, delivery);
        then(delivery).shouldHaveNoInteractions();
    }
    @Test
    @DisplayName("삭제 이벤트와 경합한 늦은 브로드캐스트는 저장된 삭제 상태로 전달한다")
    void givenDeletedMessage_whenLateBroadcast_thenDeliverLatestDeletedState() {
        given(reader.lockIfPresent(10L)).willReturn(true);
        given(reader.findById(10L)).willReturn(Optional.of(ChatRoom.createGroup(1L)));
        var card = new org.son.haruharu.domain.chat.implement.ChatMission(3L,1L,"DAY1_1","오늘 첫 미션");
        var deleted = new org.son.haruharu.domain.chat.implement.ChatMessage(2L,10L,9L,"원래 문구",java.time.LocalDateTime.now(),card,true);
        given(messages.findById(2L)).willReturn(Optional.of(deleted));
        var original = new org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast(2L,10L,9L,"원래 문구",deleted.createdAt());
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        new ChatDeliveryService(reader,messages, org.mockito.Mockito.mock(org.son.haruharu.domain.chat.implement.ChatMissionPresenter.class)).deliverMessage(original, latest -> {
            called.set(true);
            org.assertj.core.api.Assertions.assertThat(latest.deleted()).isTrue();
            org.assertj.core.api.Assertions.assertThat(latest.content()).isEqualTo("삭제된 메시지");
            org.assertj.core.api.Assertions.assertThat(latest.mission()).isNull();
        });
        org.assertj.core.api.Assertions.assertThat(called).isTrue();
    }
}
