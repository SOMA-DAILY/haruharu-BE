package org.son.haruharu.domain.room.business.dto;

import java.util.List;

/**
 * 방 멤버 목록. 항상 노출(닉네임·성별·지역·나이)만 담는다. Day 게이트 항목은 단건 프로필 조회에서 다룬다.
 * userId는 신고(reported_user_id) 연결용. profileImageUrl은 대표사진 presigned GET URL(없으면 null)로,
 * 공개 정책은 단건 프로필과 동일하게 URL은 항상 반환하고 블러 처리는 클라이언트가 담당한다.
 */
public record MembersResponse(List<MemberView> members) {

    public record MemberView(
            Long roomMemberId,
            Long userId,
            boolean me,
            String nickname,
            String gender,
            String location,
            Integer age,
            String profileImageUrl
    ) {}
}
