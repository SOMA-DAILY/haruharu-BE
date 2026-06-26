package org.son.haruharu.domain.mission.implement;

import java.util.UUID;

/** 미션(참조 데이터). 미션 영상 업로드 시 points만큼 열쇠를 지급한다(#40). */
public record Mission(
        UUID id,
        String description,
        int points,
        boolean isActive
) {
    public static Mission of(String description, int points) {
        return new Mission(null, description, points, true);
    }
}
