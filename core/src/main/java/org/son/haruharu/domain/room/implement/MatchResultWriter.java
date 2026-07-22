package org.son.haruharu.domain.room.implement;

import org.springframework.stereotype.Component;

@Component
public class MatchResultWriter {

    private final MatchResultRepository matchResultRepository;

    public MatchResultWriter(MatchResultRepository matchResultRepository) {
        this.matchResultRepository = matchResultRepository;
    }

    public MatchResult save(MatchResult matchResult) {
        return matchResultRepository.save(matchResult);
    }

    /** 데모 정리 전용 하드삭제(#266). */
    public void hardDeleteByMemberIds(java.util.List<Long> memberIds) {
        matchResultRepository.hardDeleteByMemberIds(memberIds);
    }
}
