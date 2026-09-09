package org.son.haruharu.domain.block.business.dto;

import java.util.List;

/** 내가 차단한 유저 id 목록. FE가 영상·프로필·그룹 메시지 숨김의 공통 필터로 사용한다. */
public record BlocksResponse(List<Long> blockedUserIds) {
}
