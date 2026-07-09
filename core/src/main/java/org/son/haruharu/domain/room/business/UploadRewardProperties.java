package org.son.haruharu.domain.room.business;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 일일 업로드 완주 보상 설정. dayNumber → 지급 구름 개수. yaml로 조정한다(코드 하드코딩 금지).
 * 예) Day1·2 완주 = 2, Day3(슬롯 3개) 완주 = 1.
 */
@ConfigurationProperties(prefix = "haruharu.room.upload-reward")
public record UploadRewardProperties(Map<Integer, Long> dailyCompletion) {

    /** 해당 Day 완주 시 지급할 구름 개수. 설정에 없으면 0(지급 안 함). */
    public long amountFor(int dayNumber) {
        if (dailyCompletion == null) {
            return 0L;
        }
        return dailyCompletion.getOrDefault(dayNumber, 0L);
    }
}
