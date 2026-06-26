package org.son.haruharu.domain.mission.implement;

import java.util.UUID;

/** 업로드 슬롯(참조 데이터). day_number별 5/5/3개, 각 슬롯은 start_hour~end_hour 시간창을 가진다. */
public record UploadSlot(
        UUID id,
        int dayNumber,
        int slotIndex,
        int startHour,
        int endHour
) {
    public static UploadSlot of(int dayNumber, int slotIndex, int startHour, int endHour) {
        return new UploadSlot(null, dayNumber, slotIndex, startHour, endHour);
    }
}
