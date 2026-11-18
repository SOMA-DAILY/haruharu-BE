package org.son.haruharu.domain.chat.implement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.implement.exception.InvalidChatSyncCursorException;
import java.util.List;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatMessageReaderSyncTest {
    @Mock ChatMessageRepository repository;

    @ParameterizedTest
    @CsvSource({"0, 1", "-1, 1", "2147483647, 100", "30, 30"})
    void givenPageSizeOutsideLimits_whenSync_thenReturnBoundedPage(int requested, int limit) {
        given(repository.findLastIdIncludingDeleted(10L)).willReturn(500L);
        given(repository.findSyncPage(10L, 0, 500, limit + 1)).willReturn(
                LongStream.rangeClosed(1, limit + 1).mapToObj(id -> new ChatMessage(id, 10L, 1L, "메시지", null)).toList());
        var page = new ChatMessageReader(repository).sync(10L, 0, null, requested);
        assertThat(page.messages()).hasSize(limit);
        assertThat(page.nextAfterId()).isEqualTo(limit);
        assertThat(page.targetId()).isEqualTo(500L);
        assertThat(page.hasMore()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"-1, 100", "100, 99", "0, -1"})
    void givenMalformedBounds_whenSync_thenReject(long after, long until) {
        assertThatThrownBy(() -> new ChatMessageReader(repository).sync(10L, after, until, 30))
                .isInstanceOf(InvalidChatSyncCursorException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 101", "101, 101"})
    void givenFutureBounds_whenSync_thenReject(long after, long until) {
        given(repository.findLastIdIncludingDeleted(10L)).willReturn(100L);
        assertThatThrownBy(() -> new ChatMessageReader(repository).sync(10L, after, until, 30))
                .isInstanceOf(InvalidChatSyncCursorException.class);
    }

    @Test
    void givenEmptyVisibleRange_whenSync_thenAdvanceToFixedTarget() {
        given(repository.findLastIdIncludingDeleted(10L)).willReturn(200L);
        given(repository.findSyncPage(10L, 100, 150, 31)).willReturn(List.of());
        var page = new ChatMessageReader(repository).sync(10L, 100, 150L, 30);
        assertThat(page.messages()).isEmpty();
        assertThat(page.targetId()).isEqualTo(150L);
        assertThat(page.nextAfterId()).isEqualTo(150L);
        assertThat(page.hasMore()).isFalse();
    }
}
