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
    @Mock Runnable delivery;

    @Test
    @DisplayName("방을 잠근 뒤 활성 상태일 때만 외부 전송을 실행한다")
    void givenActiveRoom_whenDeliver_thenRunsAfterLock() {
        given(reader.lockIfPresent(10L)).willReturn(true);
        given(reader.findById(10L)).willReturn(Optional.of(ChatRoom.createDirect(100L)));
        new ChatDeliveryService(reader).deliverIfActive(10L, delivery);
        var order = inOrder(reader, delivery);
        order.verify(reader).lockIfPresent(10L);
        order.verify(reader).findById(10L);
        order.verify(delivery).run();
    }
    @Test
    @DisplayName("물리 삭제 또는 소프트 삭제된 방이면 외부 전송을 생략한다")
    void givenMissingOrClosedRoom_whenDeliver_thenSkips() {
        new ChatDeliveryService(reader).deliverIfActive(10L, delivery);
        then(reader).should(never()).findById(10L);
        given(reader.lockIfPresent(10L)).willReturn(true);
        given(reader.findById(10L)).willReturn(Optional.empty());
        new ChatDeliveryService(reader).deliverIfActive(10L, delivery);
        then(delivery).shouldHaveNoInteractions();
    }
}
