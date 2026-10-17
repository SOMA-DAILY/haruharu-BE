package org.son.haruharu.domain.chat.business;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import static org.assertj.core.api.Assertions.*;

class ChatRoomLifecyclePolicyTest {
    private final ChatRoomLifecyclePolicy policy = new ChatRoomLifecyclePolicy();

    @Test
    void openGroupDoesNotReadSourceRoomForRealtimeChecks() {
        assertThat(policy.isActive(ChatRoom.createGroup(1L))).isTrue();
    }

    @Test
    void materializedClosureRejectsNewWritesAndPreservesFirstCloseTime() {
        var closedAt = LocalDateTime.of(2026, 10, 13, 22, 0);
        var closed = ChatRoom.createGroup(1L).close(closedAt);
        assertThat(policy.isActive(closed)).isFalse();
        assertThatThrownBy(() -> policy.requireActive(closed))
                .isInstanceOf(org.son.haruharu.domain.chat.business.exception.ChatRoomClosedException.class);
        assertThat(closed.close(closedAt.plusSeconds(10)).closeAt()).isEqualTo(closedAt);
    }

    @Test
    void directChatRemainsIndependentOfGroupClosure() {
        assertThat(policy.isActive(ChatRoom.createDirect(1L))).isTrue();
    }
}
