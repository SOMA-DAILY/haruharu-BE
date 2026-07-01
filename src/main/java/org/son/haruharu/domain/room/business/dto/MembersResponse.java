package org.son.haruharu.domain.room.business.dto;

import java.util.List;
import java.util.UUID;

/**
 * 방 멤버 목록 + 내 열쇠 잔액(point_sum).
 * 항상 노출(닉네임·성별·지역·나이)만 담는다. 해금 대상 항목은 단건 프로필 조회에서 다룬다.
 */
public record MembersResponse(int myPointSum, List<MemberView> members) {

    public record MemberView(
            UUID roomMemberId,
            boolean me,
            String nickname,
            String gender,
            String location,
            Integer age
    ) {}
}
