package org.son.haruharu.domain.room.application.dto;

/** 최종선택 요청. selectedMemberId=null이면 "아무도 선택하지 않기"(기권)로 처리된다. */
public record FinalSelectionRequest(
        Long selectedMemberId
) {}
