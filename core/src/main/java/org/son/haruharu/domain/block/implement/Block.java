package org.son.haruharu.domain.block.implement;

import java.time.LocalDateTime;

/** 사용자 차단(#408). 차단자·대상 쌍당 1건(중복 차단 불가). 방·1:1 어디에서 차단하든 동일하게 적용된다. */
public record Block(
        Long id,
        Long blockerUserId,
        Long blockedUserId,
        LocalDateTime createdAt
) {
    public static Block of(Long blockerUserId, Long blockedUserId, LocalDateTime createdAt) {
        return new Block(null, blockerUserId, blockedUserId, createdAt);
    }
}
