package org.son.haruharu.domain.room.business.dto;

/** 프로필 항목 해금 결과. 해금된 항목의 값과 차감 후 남은 열쇠 잔액을 반환한다. */
public record ProfileFieldUnlockResponse(
        String fieldKey,
        String value,
        int remainingPointSum
) {}
