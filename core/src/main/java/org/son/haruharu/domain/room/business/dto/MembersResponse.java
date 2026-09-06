package org.son.haruharu.domain.room.business.dto;

import java.util.List;

/**
 * 방 멤버 목록. 성별은 항상 노출하고, 지역·나이는 단건 프로필과 동일하게 3일차부터 노출한다
 * (그 전에는 null, 본인 항목은 게이트 제외 — #214). 나머지 Day 게이트 항목은 단건 프로필 조회에서 다룬다.
 * nickname 필드에는 표시 이름이 담긴다(#393): 본인이거나 상호매칭 성립 시 실명(닉네임), 그 전에는 가명이며 nameRevealed로 구분한다.
 * userId는 신고(reported_user_id) 연결용. profileImageUrl은 대표사진 presigned GET URL(없으면 null)로,
 * 공개 정책은 단건 프로필과 동일하게 URL은 항상 반환하고 블러 처리는 클라이언트가 담당한다.
 */
public record MembersResponse(List<MemberView> members) {

    public record MemberView(
            Long roomMemberId,
            Long userId,
            boolean me,
            String nickname,
            boolean nameRevealed,
            String gender,
            String location,
            Integer age,
            String profileImageUrl
    ) {}
}
