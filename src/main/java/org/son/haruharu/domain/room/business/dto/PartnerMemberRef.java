package org.son.haruharu.domain.room.business.dto;

/**
 * 매칭 상대 방 멤버 참조. 채팅 등 타 도메인이 매칭 상대의 프로필·영상을 조회할 때
 * matchResultId + 요청자 userId로부터 상대 roomMemberId·roomId를 해석해 전달한다.
 */
public record PartnerMemberRef(
        Long roomId,
        Long partnerMemberId,
        Long partnerUserId
) {}
