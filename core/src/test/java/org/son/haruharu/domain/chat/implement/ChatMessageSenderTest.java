package org.son.haruharu.domain.chat.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.common.exception.BadRequestException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageSenderTest {
    @Test
    @DisplayName("일반 메시지는 발신자 없이 생성할 수 없다")
    void givenMissingUser_whenCreateMessage_thenRejects() {
        assertThatThrownBy(() -> ChatMessage.create(1L, null, "안녕"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("시스템 발신자는 계정 없이 생성되며 유저의 소유 메시지가 아니다")
    void givenSystem_whenCreate_thenNoUserOwner() {
        ChatMessage system = ChatMessage.system(1L, "공지", 10L);
        assertThat(system.senderId()).isNull();
        assertThat(system.senderType()).isEqualTo(ChatSenderType.SYSTEM);
        assertThat(system.messageType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(system.senderName()).isEqualTo("하루");
        assertThat(system.isOwnedBy(null)).isFalse();
        assertThat(system.isOwnedBy(1L)).isFalse();
        assertThat(ChatMessage.create(1L, 2L, "안녕").isOwnedBy(2L)).isTrue();
    }

    @Test
    @DisplayName("null 유형과 SYSTEM+유저 ID 조합은 복원할 때도 거부한다")
    void givenInvalidSenderCombination_whenRestore_thenRejects() {
        assertThatThrownBy(() -> restore(null, 1L)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> restore(null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> restore(ChatSenderType.USER, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> restore(ChatSenderType.SYSTEM, 1L)).isInstanceOf(BadRequestException.class);
    }

    private ChatMessage restore(ChatSenderType type, Long sender) {
        return new ChatMessage(1L, 1L, sender, "공지", null, null, null, null, null, null,
                ChatMessageType.TEXT, null, null, null, type);
    }
}
