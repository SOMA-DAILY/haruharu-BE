package org.son.haruharu.domain.chat.business.dto;

import org.son.haruharu.domain.profile.business.dto.ProfileResponse;

/** 매칭 상대의 공개 프로필. profile 도메인의 ProfileResponse를 그대로 감싸 재사용한다. */
public record PartnerProfileResponse(
        Long userId,
        ProfileResponse profile
) {}
