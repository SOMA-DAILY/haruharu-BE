package org.son.haruharu.domain.room.business.dto;

import java.util.List;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.implement.Room;

/**
 * 오늘 화면: 현재 Day의 업로드 슬롯(시간창·현재 열림 여부) + 내 업로드 진행.
 * PENDING(currentDay=0) 등 슬롯이 없는 상태면 slots는 빈 목록.
 * JSON 직렬화는 전역 SNAKE_CASE 설정을 따른다.
 * MVP: 미션 비활성화로 미션 후보(missionCandidates)는 응답에서 제외.
 */
public record TodayResponse(
        int currentDay,
        String status,
        int totalSlots,
        long uploadedCount,
        long rewardAmount,
        RewardStatus rewardStatus,
        List<SlotView> slots
) {
    public record SlotView(int slotIndex, int startHour, int endHour, boolean openNow) {}

    /** 완주 보상 상태. EARNED=이미 지급됨, LOCKED=아직 미지급. */
    public enum RewardStatus { LOCKED, EARNED }

    public static TodayResponse of(Room room, int currentDay, List<UploadSlot> slots, int nowHour,
                                   long uploadedCount, long rewardAmount, RewardStatus rewardStatus) {
        List<SlotView> slotViews = slots.stream()
                .map(s -> new SlotView(s.slotIndex(), s.startHour(), s.endHour(),
                        nowHour >= s.startHour() && nowHour < s.endHour()))
                .toList();
        return new TodayResponse(
                currentDay, room.status().name(), slots.size(), uploadedCount,
                rewardAmount, rewardStatus, slotViews);
    }
}
