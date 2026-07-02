package org.son.haruharu.domain.mission.implement;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 업로드 슬롯(참조 데이터, 코드가 진실의 원천). Day별 개수·시간창은 정적 규칙이라 enum 상수로 정의한다.
 * 시간창·일수 변경은 상수 수정 + 배포로 반영한다. 슬롯 식별은 (dayNumber, slotIndex) 자연키.
 */
public enum UploadSlot {
    DAY1_SLOT1(1, 1, 9, 12),
    DAY1_SLOT2(1, 2, 12, 15),
    DAY1_SLOT3(1, 3, 15, 18),
    DAY1_SLOT4(1, 4, 18, 21),
    DAY1_SLOT5(1, 5, 21, 24),
    DAY2_SLOT1(2, 1, 9, 12),
    DAY2_SLOT2(2, 2, 12, 15),
    DAY2_SLOT3(2, 3, 15, 18),
    DAY2_SLOT4(2, 4, 18, 21),
    DAY2_SLOT5(2, 5, 21, 24),
    DAY3_SLOT1(3, 1, 9, 12),
    DAY3_SLOT2(3, 2, 12, 15),
    DAY3_SLOT3(3, 3, 15, 18),
    DAY3_SLOT4(3, 4, 18, 21),
    DAY3_SLOT5(3, 5, 21, 24);

    private final int dayNumber;
    private final int slotIndex;
    private final int startHour;
    private final int endHour;

    UploadSlot(int dayNumber, int slotIndex, int startHour, int endHour) {
        this.dayNumber = dayNumber;
        this.slotIndex = slotIndex;
        this.startHour = startHour;
        this.endHour = endHour;
    }

    public int dayNumber() {
        return dayNumber;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public int startHour() {
        return startHour;
    }

    public int endHour() {
        return endHour;
    }

    /** 해당 Day의 슬롯(선언 순서 = slotIndex 순). currentDay=0(미시작)이면 빈 목록. */
    public static List<UploadSlot> ofDay(int dayNumber) {
        return Arrays.stream(values()).filter(s -> s.dayNumber == dayNumber).toList();
    }

    /**
     * 하루가 시작되는(=첫 슬롯이 열리는) 시각. currentDay 파생 로직의 단일 기준값으로 사용한다.
     * (방 활성화 스케줄의 cron 정합 검증 연결은 후속 서브스텝에서 추가)
     */
    public static int dayStartHour() {
        return DAY1_SLOT1.startHour;
    }

    public static Optional<UploadSlot> of(int dayNumber, int slotIndex) {
        return Arrays.stream(values())
                .filter(s -> s.dayNumber == dayNumber && s.slotIndex == slotIndex)
                .findFirst();
    }

    public boolean isOpenAt(int hour) {
        return hour >= startHour && hour < endHour;
    }
}
