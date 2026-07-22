package org.son.haruharu.demo.dto;

import jakarta.validation.constraints.NotNull;
import org.son.haruharu.domain.room.business.DemoMatchOutcome;

/** 데모 최종선택 결과 요청(#266). outcome은 MATCHED | FAILED. */
public record DemoMatchResultRequest(
        @NotNull DemoMatchOutcome outcome
) {
    public DemoMatchOutcome toOutcome() {
        return outcome;
    }
}
