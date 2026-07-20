package org.son.haruharu.domain.user.implement;

import java.time.LocalDateTime;

public record User(
        Long id,
        UserStatus status,
        LocalDateTime createdAt,
        boolean demoMode
) {
    /** demoMode 미지정 생성(기본 false) — 데모와 무관한 일반 경로용. */
    public User(Long id, UserStatus status, LocalDateTime createdAt) {
        this(id, status, createdAt, false);
    }
}
