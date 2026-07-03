package org.son.haruharu.domain.room.business.dto;

/** 최종선택 결과. 상대도 나를 선택했으면 matched=true(즉시 매칭 성사). */
public record FinalSelectionResponse(
        Long selectedMemberId,
        boolean matched
) {}
