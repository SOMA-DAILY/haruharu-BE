package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.son.haruharu.domain.room.implement.Room;

/**
 * 오늘 화면: 공개 가능한 영상이 있는 시간대와 ACTIVE 현재 촬영 시간대를 담은 슬롯 목록 + 내 업로드 진행.
 * 원본 노출 창(slot_start 기준 현재 1시간)의 빈 비현재 슬롯은 제외한다.
 * 슬롯이 없는 상태(시작 전·종료 등)면 slots는 빈 목록.
 * JSON 직렬화는 전역 SNAKE_CASE 설정을 따른다.
 * MVP: 미션 비활성화로 미션 후보(missionCandidates)는 응답에서 제외.
 *
 * 헤더 타이머는 '남은 시간'을 서버가 계산하지 않고, 다음 전환 절대시각(timerTargetAt)만 내려
 * 클라가 자체 시계로 카운트다운한다(홈의 applyDeadlineAt/confirmAt과 동일 철학).
 * now는 응답 생성 시점 기준 시각(주입 Clock, dev 가상 시계 반영) — 클라 카운트다운의 기기 시계 오차 보정 기준.
 * timerType으로 라벨을 구분한다: NEXT_SLOT="다음 시간대", DEADLINE="마감".
 * currentDay는 방 진행 일차 표시용(room 레벨).
 */
public record TodayResponse(
        LocalDateTime now,
        int currentDay,
        String status,
        int totalSlots,
        long uploadedCount,
        LocalDateTime timerTargetAt,
        TimerType timerType,
        List<SlotView> slots
) {
    /** openNow=지금 촬영 가능한 현재 슬롯인지. uploaded=요청 유저 본인이 이 슬롯에 업로드했는지(개인화). */
    public record SlotView(LocalDateTime slotStart, boolean openNow, boolean uploaded) {}

    /** 헤더 타이머가 가리키는 대상 종류. NEXT_SLOT=다음 슬롯 시작, DEADLINE=최종선택/결과 마감. */
    public enum TimerType { NEXT_SLOT, DEADLINE }

    public static TodayResponse of(Room room, LocalDateTime now, int currentDay,
                                   List<LocalDateTime> window, LocalDateTime currentSlotStart,
                                   Set<LocalDateTime> uploadedSlots,
                                   LocalDateTime timerTargetAt, TimerType timerType) {
        List<SlotView> slotViews = window.stream()
                .map(slotStart -> new SlotView(slotStart,
                        slotStart.equals(currentSlotStart),
                        uploadedSlots.contains(slotStart)))
                .toList();
        long uploadedInWindow = slotViews.stream().filter(SlotView::uploaded).count();
        return new TodayResponse(
                now, currentDay, room.statusAt(now).name(), window.size(), uploadedInWindow,
                timerTargetAt, timerType, slotViews);
    }
}
