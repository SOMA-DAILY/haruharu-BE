package org.son.haruharu.domain.room.business.dto;

import java.util.List;
import java.util.UUID;

/**
 * 방 멤버 목록 + 내 열쇠 잔액(point_sum).
 * 공개 규칙(와이어 img_2/img_3): 닉네임·지역·나이·성별은 항상 노출, 직업/학교(affiliation)·키(height)·MBTI는 Day3부터.
 * 프로필 사진은 #39 범위 밖(와이어상 얼굴 미노출). JSON은 전역 SNAKE_CASE.
 */
public record MembersResponse(int myPointSum, List<MemberView> members) {

    public record MemberView(
            UUID roomMemberId,
            boolean me,
            String nickname,
            String gender,
            String location,
            Integer age,
            String affiliation,
            Integer height,
            String mbti
    ) {}
}
