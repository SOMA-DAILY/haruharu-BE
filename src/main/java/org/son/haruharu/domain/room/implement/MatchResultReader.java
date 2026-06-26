package org.son.haruharu.domain.room.implement;

import org.son.haruharu.domain.room.storage.exception.MatchResultNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MatchResultReader {

    private final MatchResultRepository matchResultRepository;

    public MatchResultReader(MatchResultRepository matchResultRepository) {
        this.matchResultRepository = matchResultRepository;
    }

    public MatchResult getByMember(UUID memberId) {
        return matchResultRepository.findByMember(memberId)
                .orElseThrow(MatchResultNotFoundException::new);
    }
}
