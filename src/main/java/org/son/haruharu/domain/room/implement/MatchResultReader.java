package org.son.haruharu.domain.room.implement;

import org.son.haruharu.domain.room.storage.exception.MatchResultNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class MatchResultReader {

    private final MatchResultRepository matchResultRepository;

    public MatchResultReader(MatchResultRepository matchResultRepository) {
        this.matchResultRepository = matchResultRepository;
    }

    public MatchResult getByMember(Long memberId) {
        return matchResultRepository.findByMember(memberId)
                .orElseThrow(MatchResultNotFoundException::new);
    }

    public MatchResult getById(Long matchResultId) {
        return matchResultRepository.findById(matchResultId)
                .orElseThrow(MatchResultNotFoundException::new);
    }

    /** 두 멤버가 서로 최종매칭 성립했는지 여부(예외 없이 boolean). */
    public boolean isMatched(Long memberId1, Long memberId2) {
        return matchResultRepository.existsBetween(memberId1, memberId2);
    }
}
