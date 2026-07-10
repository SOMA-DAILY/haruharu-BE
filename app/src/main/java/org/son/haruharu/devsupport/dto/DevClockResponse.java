package org.son.haruharu.devsupport.dto;

import java.time.LocalDateTime;

/** fixed=false면 now는 실시간. expiresAt은 고정이 자동 해제되는 실시간 시각(TTL). */
public record DevClockResponse(
        boolean fixed,
        LocalDateTime now,
        LocalDateTime realNow,
        LocalDateTime expiresAt
) {}
