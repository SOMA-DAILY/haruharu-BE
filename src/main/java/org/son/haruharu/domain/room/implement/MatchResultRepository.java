package org.son.haruharu.domain.room.implement;

import java.util.Optional;

public interface MatchResultRepository {

    Optional<MatchResult> findByMember(Long memberId);

    MatchResult save(MatchResult matchResult);
}
