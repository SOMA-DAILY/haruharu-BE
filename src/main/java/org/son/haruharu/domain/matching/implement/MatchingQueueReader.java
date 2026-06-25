package org.son.haruharu.domain.matching.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.matching.storage.exception.MatchingQueueNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MatchingQueueReader {

    private final MatchingQueueRepository matchingQueueRepository;

    public boolean isQueued(UUID userId) {
        return matchingQueueRepository.existsByUserIdAndStatus(userId, MatchingQueueStatus.QUEUED);
    }

    public MatchingQueue getQueued(UUID userId) {
        return matchingQueueRepository.findByUserIdAndStatus(userId, MatchingQueueStatus.QUEUED)
                .orElseThrow(MatchingQueueNotFoundException::new);
    }

    /** 매칭 배치용: 대기(QUEUED) 항목을 신청 순서(queuedAt)대로 전부 조회한다. */
    public List<MatchingQueue> findAllQueued() {
        return matchingQueueRepository.findAllByStatusOrderByQueuedAt(MatchingQueueStatus.QUEUED);
    }
}
