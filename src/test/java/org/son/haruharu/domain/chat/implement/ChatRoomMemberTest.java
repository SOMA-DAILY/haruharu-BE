package org.son.haruharu.domain.chat.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomMemberTest {

    private ChatRoomMember member(Long lastReadMessageId) {
        return new ChatRoomMember(1L, 10L, 100L, LocalDateTime.now(), null, lastReadMessageId);
    }

    @Test
    @DisplayName("읽음 커서는 더 큰(최신) 메시지 id로만 전진한다")
    void readUpTo_movesForwardOnly() {
        assertThat(member(5L).readUpTo(8L).lastReadMessageId()).isEqualTo(8L);
    }

    @Test
    @DisplayName("읽음 커서보다 작은(과거) id로 갱신하면 커서가 역행하지 않는다")
    void readUpTo_doesNotMoveBackward() {
        assertThat(member(5L).readUpTo(3L).lastReadMessageId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("한 번도 읽지 않은(null 커서) 멤버는 커서가 지정 id로 설정된다")
    void readUpTo_fromNull_setsCursor() {
        assertThat(member(null).readUpTo(4L).lastReadMessageId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("방 나가기 시 leftAt이 기록되고 hasLeft가 true가 된다")
    void leave_recordsLeftAt() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 3, 12, 0);

        ChatRoomMember left = member(null).leave(now);

        assertThat(left.leftAt()).isEqualTo(now);
        assertThat(left.hasLeft()).isTrue();
    }
}
