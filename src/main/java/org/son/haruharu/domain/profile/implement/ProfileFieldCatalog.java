package org.son.haruharu.domain.profile.implement;

import java.util.UUID;

/**
 * 해금 가능한 프로필 항목의 정책(비용·해금 가능 Day). 운영 중 데이터로 조정 가능해 기획 변동을 코드 수정 없이 흡수한다.
 */
public record ProfileFieldCatalog(
        UUID id,
        String fieldKey,
        int unlockCost,
        int availableFromDay,
        boolean active
) {
    public static ProfileFieldCatalog of(String fieldKey, int unlockCost, int availableFromDay) {
        return new ProfileFieldCatalog(null, fieldKey, unlockCost, availableFromDay, true);
    }

    /** currentDay가 이 항목의 해금 가능 시점에 도달했는지. Day 게이트 단일 판정처(조회·해금 양쪽에서 재사용). */
    public boolean isUnlockableOn(int currentDay) {
        return currentDay >= availableFromDay;
    }
}
