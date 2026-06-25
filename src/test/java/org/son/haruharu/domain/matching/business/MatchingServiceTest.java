package org.son.haruharu.domain.matching.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.matching.business.dto.QueueResponse;
import org.son.haruharu.domain.matching.business.exception.MatchingAlreadyQueuedException;
import org.son.haruharu.domain.matching.business.exception.MatchingNotVerifiedException;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueReader;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.matching.implement.MatchingQueueWriter;
import org.son.haruharu.domain.matching.storage.exception.MatchingQueueNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock MatchingQueueReader matchingQueueReader;
    @Mock MatchingQueueWriter matchingQueueWriter;
    @Mock IdentityService identityService;

    MatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new MatchingService(matchingQueueReader, matchingQueueWriter, identityService);
    }

    @Test
    @DisplayName("미인증 유저가 매칭 큐 참가 시 MatchingNotVerifiedException이 발생하고 저장하지 않는다")
    void givenUnverified_whenJoinQueue_thenThrowsNotVerified() {
        UUID userId = UUID.randomUUID();
        given(identityService.findVerifiedGender(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> matchingService.joinQueue(userId))
            .isInstanceOf(MatchingNotVerifiedException.class);

        then(matchingQueueWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 큐에 있는 유저가 다시 참가 시 MatchingAlreadyQueuedException이 발생한다")
    void givenAlreadyQueued_whenJoinQueue_thenThrowsAlreadyQueued() {
        UUID userId = UUID.randomUUID();
        given(identityService.findVerifiedGender(userId)).willReturn(Optional.of("MALE"));
        given(matchingQueueReader.isQueued(userId)).willReturn(true);

        assertThatThrownBy(() -> matchingService.joinQueue(userId))
            .isInstanceOf(MatchingAlreadyQueuedException.class);

        then(matchingQueueWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("인증된 미참가 유저가 큐 참가 시 QUEUED 상태로 저장하고 성별을 응답한다")
    void givenVerifiedNotQueued_whenJoinQueue_thenSavesQueued() {
        UUID userId = UUID.randomUUID();
        given(identityService.findVerifiedGender(userId)).willReturn(Optional.of("MALE"));
        given(matchingQueueReader.isQueued(userId)).willReturn(false);
        given(matchingQueueWriter.save(any())).willAnswer(inv -> {
            MatchingQueue q = inv.getArgument(0);
            return new MatchingQueue(UUID.randomUUID(), q.userId(), q.gender(), q.status(), q.queuedAt(), q.matchedAt());
        });

        QueueResponse result = matchingService.joinQueue(userId);

        assertThat(result.gender()).isEqualTo("MALE");
        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(result.queueId()).isNotNull();
        then(matchingQueueWriter).should().save(any());
    }

    @Test
    @DisplayName("큐에 있는 유저가 취소 시 CANCELLED 상태로 저장한다")
    void givenQueued_whenLeaveQueue_thenSavesCancelled() {
        UUID userId = UUID.randomUUID();
        MatchingQueue queued = new MatchingQueue(
            UUID.randomUUID(), userId, "MALE", MatchingQueueStatus.QUEUED, LocalDateTime.now(), null);
        given(matchingQueueReader.getQueued(userId)).willReturn(queued);

        matchingService.leaveQueue(userId);

        ArgumentCaptor<MatchingQueue> captor = ArgumentCaptor.forClass(MatchingQueue.class);
        then(matchingQueueWriter).should().save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(MatchingQueueStatus.CANCELLED);
    }

    @Test
    @DisplayName("큐에 없는 유저가 취소 시 MatchingQueueNotFoundException이 발생한다")
    void givenNotQueued_whenLeaveQueue_thenThrows() {
        UUID userId = UUID.randomUUID();
        given(matchingQueueReader.getQueued(userId)).willThrow(new MatchingQueueNotFoundException());

        assertThatThrownBy(() -> matchingService.leaveQueue(userId))
            .isInstanceOf(MatchingQueueNotFoundException.class);

        then(matchingQueueWriter).should(never()).save(any());
    }
}
