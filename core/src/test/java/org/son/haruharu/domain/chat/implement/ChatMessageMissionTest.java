package org.son.haruharu.domain.chat.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.common.exception.BadRequestException;
import org.son.haruharu.domain.chat.storage.ChatMessageEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageMissionTest {

    @Test
    @DisplayName("미션 안내는 계정 없는 시스템 메시지로 미션과 개별 안내를 참조한다")
    void givenMissionAnnouncement_whenCreate_thenReferencesMissionWithoutUserSender() {
        ChatMessage message = ChatMessage.mission(1L, "오늘의 대화를 시작해 주세요", 10L, 101L);

        assertThat(message.messageType()).isEqualTo(ChatMessageType.MISSION);
        assertThat(message.senderType()).isEqualTo(ChatSenderType.SYSTEM);
        assertThat(message.senderId()).isNull();
        assertThat(message.roomMissionId()).isEqualTo(10L);
        assertThat(message.missionAnnouncementId()).isEqualTo(101L);
        assertThat(message.scheduleItemId()).isNull();
        assertThat(message.isOwnedBy(2L)).isFalse();
    }

    @Test
    @DisplayName("미션과 안내 중 하나라도 없거나 유효한 ID가 아니면 미션 메시지를 생성할 수 없다")
    void givenIncompleteMissionReference_whenCreate_thenRejects() {
        assertThatThrownBy(() -> ChatMessage.mission(1L, "안내", null, 101L))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ChatMessage.mission(1L, "안내", 10L, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ChatMessage.mission(1L, "안내", 0L, 101L))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ChatMessage.mission(1L, "안내", 10L, -1L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("일반 메시지는 미션 참조를 가질 수 없고 미션 안내의 발신자는 SYSTEM이어야 한다")
    void givenInvalidTypeOrSender_whenRestore_thenRejects() {
        assertThatThrownBy(() -> restore(ChatMessageType.TEXT, ChatSenderType.SYSTEM, null, 10L, 101L))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> restore(ChatMessageType.TEXT, ChatSenderType.USER, 2L, null, 101L))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> restore(ChatMessageType.MISSION, ChatSenderType.USER, 2L, 10L, 101L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("미션 안내에 대한 일반 답장은 TEXT로 저장하고 원문 인용만 유지한다")
    void givenMissionMessage_whenUserReplies_thenCreatesTextReply() {
        ChatMessage original = restore(ChatMessageType.MISSION, ChatSenderType.SYSTEM, null, 10L, 101L);

        ChatMessage reply = ChatMessage.reply(1L, 2L, "참여할게요", original, "하루");

        assertThat(reply.messageType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(reply.replyToMessageId()).isEqualTo(original.id());
        assertThat(reply.roomMissionId()).isNull();
        assertThat(reply.missionAnnouncementId()).isNull();
    }

    @Test
    @DisplayName("미션 메시지를 영속 모델로 왕복해도 참조와 게시 당시 본문·시각을 보존한다")
    void givenMissionMessage_whenEntityRoundTrip_thenPreservesReferenceAndSnapshot() {
        ChatMessage original = restore(ChatMessageType.MISSION, ChatSenderType.SYSTEM, null, 10L, 101L);

        assertThat(ChatMessageEntity.from(original).toDomain()).isEqualTo(original);
    }

    @Test
    @DisplayName("기존 일반 메시지와 시스템 공지는 미션 참조 없이 TEXT로 유지된다")
    void givenExistingMessageFactories_whenCreate_thenKeepsTextContract() {
        for (ChatMessage message : new ChatMessage[]{
                ChatMessage.create(1L, 2L, "안녕하세요"), ChatMessage.system(1L, "공지", 5L)}) {
            assertThat(message.messageType()).isEqualTo(ChatMessageType.TEXT);
            assertThat(message.roomMissionId()).isNull();
            assertThat(message.missionAnnouncementId()).isNull();
        }
    }

    private ChatMessage restore(ChatMessageType type, ChatSenderType senderType, Long senderId,
                                Long missionId, Long announcementId) {
        return new ChatMessage(500L, 1L, senderId, "게시 당시의 안내", LocalDateTime.of(2026, 10, 7, 12, 0),
                null, null, null, null, null, type, null, null, null, senderType,
                missionId, announcementId);
    }
}
