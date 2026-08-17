package org.son.haruharu.domain.room.business.dto;

/**
 * 최종선택 제출 결과. 동시공개(#343)라 제출 시점엔 매칭 성사 여부를 알 수 없다 —
 * 결과는 result_at 이후 매치 결과 조회로 공개된다. selectedMemberId는 기권 시 null.
 */
public record FinalSelectionResponse(
        Long selectedMemberId,
        boolean submitted
) {
    /** 상대를 선택해 제출. */
    public static FinalSelectionResponse submitted(Long selectedMemberId) {
        return new FinalSelectionResponse(selectedMemberId, true);
    }

    /** 기권(아무도 선택 안 함) 제출. */
    public static FinalSelectionResponse abstained() {
        return new FinalSelectionResponse(null, true);
    }
}
