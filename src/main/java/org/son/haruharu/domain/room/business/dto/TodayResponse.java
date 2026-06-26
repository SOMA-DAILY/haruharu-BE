package org.son.haruharu.domain.room.business.dto;

import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.implement.Room;

import java.util.List;
import java.util.UUID;

/**
 * 오늘 화면: 현재 Day의 업로드 슬롯(시간창·현재 열림 여부) + 미션 후보 + 내 업로드 진행.
 * PENDING(currentDay=0) 등 슬롯이 없는 상태면 slots는 빈 목록.
 * JSON 직렬화는 전역 SNAKE_CASE 설정을 따른다.
 */
public record TodayResponse(
        int currentDay,
        String status,
        int totalSlots,
        long uploadedCount,
        List<SlotView> slots,
        List<MissionView> missionCandidates
) {
    public record SlotView(int slotIndex, int startHour, int endHour, boolean openNow) {}

    public record MissionView(UUID missionId, String description, int points) {}

    public static TodayResponse of(Room room, List<UploadSlot> slots, int nowHour,
                                   long uploadedCount, List<Mission> missions) {
        List<SlotView> slotViews = slots.stream()
                .map(s -> new SlotView(s.slotIndex(), s.startHour(), s.endHour(),
                        nowHour >= s.startHour() && nowHour < s.endHour()))
                .toList();
        List<MissionView> missionViews = missions.stream()
                .map(m -> new MissionView(m.id(), m.description(), m.points()))
                .toList();
        return new TodayResponse(
                room.currentDay(), room.status().name(), slots.size(), uploadedCount, slotViews, missionViews);
    }
}
