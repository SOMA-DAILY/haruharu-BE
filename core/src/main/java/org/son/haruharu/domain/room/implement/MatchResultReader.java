package org.son.haruharu.domain.room.implement;

import org.son.haruharu.domain.room.storage.exception.MatchResultNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

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

    /** 해당 멤버의 상호매칭 성사 여부(예외 없이 boolean, 위로 구름 대상 판정용). */
    public boolean hasMatch(Long memberId) {
        return matchResultRepository.findByMember(memberId).isPresent();
    }

    /** 해당 멤버의 매칭 결과(없으면 empty — 미성사도 정상 흐름인 조회용). */
    public Optional<MatchResult> findByMember(Long memberId) {
        return matchResultRepository.findByMember(memberId);
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
