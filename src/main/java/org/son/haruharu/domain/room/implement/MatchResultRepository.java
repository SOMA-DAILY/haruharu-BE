package org.son.haruharu.domain.room.implement;

import java.util.Optional;
import java.util.UUID;

public interface MatchResultRepository {

    Optional<MatchResult> findByMember(UUID memberId);

    MatchResult save(MatchResult matchResult);
}
