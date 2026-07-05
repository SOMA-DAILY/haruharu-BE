package org.son.haruharu.domain.chat.business.dto;

/** 채팅 상대의 최소 정보(목록·상세 공용). */
public record PartnerBrief(
        Long userId,
        String nickname
) {}
