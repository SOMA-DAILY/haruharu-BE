package org.son.haruharu.domain.room.business.dto;

import java.util.List;

/**
 * 방 멤버 목록. 항상 노출(닉네임·성별·지역·나이)만 담는다. Day 게이트 항목은 단건 프로필 조회에서 다룬다.
 */
public record MembersResponse(List<MemberView> members) {

    public record MemberView(
            Long roomMemberId,
            boolean me,
            String nickname,
            String gender,
            String location,
            Integer age
    ) {}
}
