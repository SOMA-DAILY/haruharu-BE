package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 상호 최종선택 성사 결과(1쌍 1행). canonical ordering(memberA < memberB)·중복 방지는 #41 앱 로직에서 보장한다
 * (CHECK·partial unique는 JPA/H2 DDL로 생성되지 않음).
 */
public record MatchResult(
        UUID id,
        UUID memberAId,
        UUID memberBId,
        LocalDateTime matchedAt
) {}
