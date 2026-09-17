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
     * 가시 슬롯 목록에서 업로드 가능 구간을 벗어난 슬롯을 뺀다. {@code uploadableUntil}(예: 방 종료 시각)
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

    /**
     * {@code before} 슬롯보다 과거의 슬롯 시작 시각들을 최대 {@code size}개(오름차순). 과거 회고 페이지네이션(#350)용.
     * <ul>
     *   <li>{@code startsAt} 미만은 제외 — 방 시작 이전 슬롯은 없다.</li>
     *   <li>{@code uploadableUntil} 이상은 제외 — 업로드 불가였던 구간(방 종료 이후).</li>
     *   <li>{@code before}가 startsAt 이하이거나 size≤0이면 빈 목록.</li>
     * </ul>
     * before는 그리드에 정렬돼 있지 않아도 된다(before "미만"의 슬롯을 그리드에서 고른다).
     * 반환 목록의 첫 원소(가장 과거)가 다음 페이지 커서 후보다 — 그게 startsAt이면 더 과거가 없다.
     */
    public static List<LocalDateTime> pastSlotStarts(LocalDateTime startsAt, LocalDateTime before,
                                                     int size, LocalDateTime uploadableUntil) {
        // 페이지 시작점을 상한(업로드 불가 구간) 이전으로 당긴다. 안 그러면 before가 상한보다 뒤일 때
        // 첫 페이지가 통째로 잘려 "더 과거 없음"으로 조기 종료된다(상한 이전 유효 슬롯에 도달 못 함, #350 리뷰).
        LocalDateTime effectiveBefore = (uploadableUntil != null && uploadableUntil.isBefore(before))
                ? uploadableUntil : before;
        if (size <= 0 || !effectiveBefore.isAfter(startsAt)) {
            return List.of();
        }
        // effectiveBefore 바로 아래 슬롯 인덱스 = ceil((effectiveBefore-startsAt)/1h) - 1. 그리드 위면 (m-1), 아니면 floor.
        long minutes = Duration.between(startsAt, effectiveBefore).toMinutes();
        long justBelow = (minutes + 59) / 60 - 1;
        long low = Math.max(0, justBelow - size + 1);
        List<LocalDateTime> result = new ArrayList<>();
        for (long i = low; i <= justBelow; i++) {
            // effectiveBefore ≤ uploadableUntil 라 모든 candidate 는 항상 상한 이내다(별도 필터 불필요).
            result.add(startsAt.plusHours(i));
        }
        return result;
    }
}
