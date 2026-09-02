package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MissionActivityEventListenerTest {

    @Mock MissionActivityCollectorService collectorService;
    private MissionActivityEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new MissionActivityEventListener(collectorService);
    }

    @Test
    @DisplayName("영상 저장 성공 이벤트는 원문 없이 VIDEO 활동 수집기로 전달한다")
    void givenVideoStoredEvent_whenHandle_thenCollectsVideoActivity() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 2, 9, 10);

        listener.handleVideo(new VideoUploadedEvent(100L, 1L, 20L, occurredAt));

        verify(collectorService).recordVideo(1L, 20L, occurredAt);
    }

    @Test
    @DisplayName("GROUP 저장 이벤트만 미션 채팅 활동으로 수집하고 DIRECT는 제외한다")
    void givenGroupAndDirectEvents_whenHandle_thenCollectsOnlyGroup() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 2, 9, 10);
        listener.handleChat(new ChatMessageSentEvent(
                100L, 10L, ChatRoomType.GROUP, 1L, 30L, List.of(), "닉네임", occurredAt));
        listener.handleChat(new ChatMessageSentEvent(
                101L, 11L, ChatRoomType.DIRECT, null, 30L, List.of(), "닉네임", occurredAt));

        verify(collectorService).recordGroupChat(1L, 30L, occurredAt);
        verify(collectorService, never()).recordGroupChat(null, 30L, occurredAt);
    }

    @Test
    @DisplayName("커밋 후 부가 활동 수집 실패는 저장 완료된 영상·채팅 요청으로 전파하지 않는다")
    void givenCollectorFailureAfterCommit_whenHandle_thenDoesNotPropagate() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 2, 9, 10);
        willThrow(new IllegalStateException("collector failure"))
                .given(collectorService).recordVideo(1L, 20L, occurredAt);
        willThrow(new IllegalStateException("collector failure"))
                .given(collectorService).recordGroupChat(1L, 30L, occurredAt);

        assertThatCode(() -> listener.handleVideo(
                new VideoUploadedEvent(100L, 1L, 20L, occurredAt)))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.handleChat(new ChatMessageSentEvent(
                101L, 10L, ChatRoomType.GROUP, 1L, 30L,
                List.of(), "닉네임", occurredAt)))
                .doesNotThrowAnyException();
    }
}
