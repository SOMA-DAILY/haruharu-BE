package org.son.haruharu.domain.matching.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MatchingQueueWriter {

    private final MatchingQueueRepository matchingQueueRepository;

    public MatchingQueue save(MatchingQueue queue) {
        return matchingQueueRepository.save(queue);
    }

    /** QUEUED일 때만 CANCELLED 전이. 성공 여부로 환불 실행을 판단한다(이중 환불 방지). */
    public boolean cancelIfQueued(UUID id, LocalDateTime now) {
        return matchingQueueRepository.transitionStatus(
                id, MatchingQueueStatus.QUEUED, MatchingQueueStatus.CANCELLED, now);
    }

    /** QUEUED일 때만 EXPIRED 전이. 배치와 유저 취소가 겹쳐도 환불은 한 번만 실행된다. */
    public boolean expireIfQueued(UUID id, LocalDateTime now) {
        return matchingQueueRepository.transitionStatus(
                id, MatchingQueueStatus.QUEUED, MatchingQueueStatus.EXPIRED, now);
    }
}
