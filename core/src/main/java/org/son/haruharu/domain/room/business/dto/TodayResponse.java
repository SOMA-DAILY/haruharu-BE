package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.implement.Room;

/**
 * 오늘 화면: 현재 Day의 업로드 슬롯(시간창·현재 열림 여부) + 내 업로드 진행.
 * PENDING(currentDay=0) 등 슬롯이 없는 상태면 slots는 빈 목록.
 * JSON 직렬화는 전역 SNAKE_CASE 설정을 따른다.
 * MVP: 미션 비활성화로 미션 후보(missionCandidates)는 응답에서 제외.
 *
 * 헤더 타이머는 '남은 시간'을 서버가 계산하지 않고, 다음 전환 절대시각(timerTargetAt)만 내려
 * 클라가 자체 시계로 카운트다운한다(홈의 applyDeadlineAt/confirmAt과 동일 철학).
 * now는 응답 생성 시점 기준 시각(주입 Clock, dev 가상 시계 반영) — 클라 카운트다운의 기기 시계 오차 보정 기준.
 * timerType으로 라벨을 구분한다: NEXT_SLOT="다음 시간대", DEADLINE="마감".
 */
public record TodayResponse(
        LocalDateTime now,
        int currentDay,
        String status,
        int totalSlots,
        long uploadedCount,
        long rewardAmount,
        RewardStatus rewardStatus,
        LocalDateTime timerTargetAt,
        TimerType timerType,
        List<SlotView> slots
) {
    /** uploaded = 요청 유저 본인이 이 슬롯에 업로드했는지(개인화). 지난 슬롯 + uploaded=false → "놓침" 판정용(#209). */
    public record SlotView(int slotIndex, int startHour, int endHour, boolean openNow, boolean uploaded) {}

    /** 완주 보상 상태. EARNED=이미 지급됨, LOCKED=아직 미지급. */
    public enum RewardStatus { LOCKED, EARNED }

    /** 헤더 타이머가 가리키는 대상 종류. NEXT_SLOT=다음 슬롯 시작, DEADLINE=최종선택/결과 마감. */
    public enum TimerType { NEXT_SLOT, DEADLINE }

    public static TodayResponse of(Room room, LocalDateTime now, int currentDay, List<UploadSlot> slots,
                                   Set<Integer> uploadedSlotIndexes, long rewardAmount, RewardStatus rewardStatus,
                                   LocalDateTime timerTargetAt, TimerType timerType) {
        int nowHour = now.getHour();
        List<SlotView> slotViews = slots.stream()
                .map(s -> new SlotView(s.slotIndex(), s.startHour(), s.endHour(),
                        nowHour >= s.startHour() && nowHour < s.endHour(),
                        uploadedSlotIndexes.contains(s.slotIndex())))
                .toList();
        // uploadedCount는 슬롯별 uploaded의 합계에서 파생 — 두 값이 어긋날 수 없다(슬롯당 활성 영상 1개 보장)
        return new TodayResponse(
                now, currentDay, room.status().name(), slots.size(), uploadedSlotIndexes.size(),
                rewardAmount, rewardStatus, timerTargetAt, timerType, slotViews);
    }
}
