package org.son.haruharu.domain.video.implement;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 영상 슬롯의 시간창 계산(참조 로직, 방 시작 상대). 슬롯은 벽시계 고정이 아니라
 * {@code slot_start = starts_at + N시간}(N≥0)으로 방마다 다르게 흐른다.
 * <ul>
 *   <li>창: {@code [slot_start, slot_start+1h)} — 한 슬롯의 업로드 대상 구간</li>
 *   <li>가시성: {@code [slot_start, slot_start+3h)} — 올린 영상이 노출되는 구간(공백 회피용 3시간 유지)</li>
 * </ul>
 * 순수 계산만 하고 상태를 갖지 않는다(now·starts_at을 인자로 받는다). Day·벽시계 개념에 결합되지 않는다.
 */
public final class SlotClock {

    /** 슬롯 길이(업로드 창). */
    public static final int SLOT_LENGTH_HOURS = 1;
    /** 가시성 유지 시간(노출 창). */
    public static final int VISIBILITY_HOURS = 3;

    private SlotClock() {
    }

    /**
     * now가 속한 슬롯의 시작 시각. {@code starts_at + floor((now-starts_at)/1h)·1h}.
     * now가 starts_at 이전이면(정상 흐름에선 statusAt==ACTIVE 게이트가 막음) starts_at을 반환한다.
     */
    public static LocalDateTime currentSlotStart(LocalDateTime startsAt, LocalDateTime now) {
        if (now.isBefore(startsAt)) {
            return startsAt;
        }
        long elapsedHours = Duration.between(startsAt, now).toHours(); // now>=startsAt이라 floor
        return startsAt.plusHours(elapsedHours);
    }

    /** 다음 슬롯 시작 시각(현재 슬롯 + 1시간). 헤더 타이머용. */
    public static LocalDateTime nextSlotStart(LocalDateTime startsAt, LocalDateTime now) {
        return currentSlotStart(startsAt, now).plusHours(SLOT_LENGTH_HOURS);
    }

    /** now가 slot_start의 가시성 창 {@code [slot_start, slot_start+3h)} 안인지. */
    public static boolean isVisible(LocalDateTime slotStart, LocalDateTime now) {
        return !now.isBefore(slotStart) && now.isBefore(slotStart.plusHours(VISIBILITY_HOURS));
    }

    /**
     * 지금 노출 중인 슬롯 시작 시각들(오름차순). 현재 슬롯과, 아직 가시성 창이 남은 과거 슬롯을 담되
     * starts_at 이전은 제외한다. 슬롯 간격 1h·가시성 3h라 최대 3개(현재+과거2).
     */
    public static List<LocalDateTime> visibleSlotStarts(LocalDateTime startsAt, LocalDateTime now) {
        LocalDateTime current = currentSlotStart(startsAt, now);
        List<LocalDateTime> result = new ArrayList<>();
        for (int back = VISIBILITY_HOURS - 1; back >= 0; back--) {
            LocalDateTime candidate = current.minusHours((long) back * SLOT_LENGTH_HOURS);
            if (!candidate.isBefore(startsAt) && isVisible(candidate, now)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * 가시 슬롯 목록에서 업로드 가능 구간을 벗어난 슬롯을 뺀다. {@code uploadableUntil}(예: 최종선택 오픈 시각)
     * 이상에서 시작하는 슬롯은 애초에 업로드가 불가능하므로(방이 ACTIVE가 아님) 피드·오늘 창에서 제외한다.
     * uploadableUntil이 null이면 캡하지 않는다.
     */
    public static List<LocalDateTime> visibleSlotStarts(LocalDateTime startsAt, LocalDateTime now,
                                                        LocalDateTime uploadableUntil) {
        List<LocalDateTime> base = visibleSlotStarts(startsAt, now);
        if (uploadableUntil == null) {
            return base;
        }
        return base.stream().filter(slotStart -> slotStart.isBefore(uploadableUntil)).toList();
    }
}
