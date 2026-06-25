package org.son.haruharu.domain.matching.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchingQueueWriter {

    private final MatchingQueueRepository matchingQueueRepository;

    public MatchingQueue save(MatchingQueue queue) {
        return matchingQueueRepository.save(queue);
    }
}
