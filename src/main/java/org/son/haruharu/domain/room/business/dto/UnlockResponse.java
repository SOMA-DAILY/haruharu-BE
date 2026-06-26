package org.son.haruharu.domain.room.business.dto;

import java.util.UUID;

/** 가치관 답변 해금 결과. 해금된 답변 내용과 차감 후 남은 열쇠 잔액을 반환한다. */
public record UnlockResponse(
        UUID answerId,
        String answer,
        int remainingPointSum
) {}
