package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record Room(
        Long id,
        RoomStatus status,
        LocalDate scheduledDate,
        int totalDays,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        // 새 MVP(#311): 운영진/어드민이 방마다 직접 입력하는 스케줄 시각. 초대코드 모델 이전 방은 전부 null.
        LocalDateTime startsAt,
        LocalDateTime finalSelectionOpensAt,
        LocalDateTime finalSelectionClosesAt,
        LocalDateTime resultAt,
        LocalDateTime endsAt
) {
    /** 6-arg 하위호환. 신규 스케줄 시각은 null default — 기존 6-arg 호출부/픽스처를 그대로 살린다. */
    public Room(Long id, RoomStatus status, LocalDate scheduledDate, int totalDays,
                LocalDateTime startedAt, LocalDateTime endedAt) {
        this(id, status, scheduledDate, totalDays, startedAt, endedAt, null, null, null, null, null);
    }
    /**
     * 세션 결과 발표(최종선택 마감·미매칭 확정) 시각. room 세션의 고유 개념으로,
     * 매칭 큐 확정 시각(MatchingQueue.CONFIRM_HOUR)과 우연히 같은 22시일 뿐 별개다 —
     * 타 도메인 implement 결합을 피하기 위해 room이 소유한다(#143 리뷰 반영).
     */
    public static final int RESULT_ANNOUNCE_HOUR = 22;

    /** 매칭 확정으로 방 생성: scheduledDate(확정일) 당일 시작 예정. 시작 전이므로 PENDING. (구 모델·보류) */
    public static Room create(LocalDate scheduledDate, int totalDays) {
        return new Room(null, RoomStatus.PENDING, scheduledDate, totalDays, null, null);
    }

    /**
     * 초대코드 모델 방 생성(#311): 운영진/어드민이 시각 4~5개를 직접 입력한다. 시작 전이므로 PENDING.
     * scheduledDate 는 구 조회 호환을 위해 startsAt 의 날짜로 채운다(구 activator 게이팅은 P4-1).
     * status 는 저장하되 timestamp 가 source of truth — 실제 상태는 statusAt(now) 로 파생한다.
     */
    public static Room createScheduled(LocalDateTime startsAt, LocalDateTime finalSelectionOpensAt,
                                       LocalDateTime finalSelectionClosesAt, LocalDateTime resultAt,
                                       LocalDateTime endsAt, int totalDays) {
        return new Room(null, RoomStatus.PENDING, startsAt.toLocalDate(), totalDays, null, null,
                startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt);
    }

    /**
     * 시각 기준 파생 상태(#311). timestamp = source of truth, 저장 status 는 materialized(배치가 나중에 동기화, P4-1).
     * 전이: PENDING(모집) → ACTIVE(진행) → SELECTING(최종선택, opensAt부터) → ENDED(endsAt).
     * CANCELLED 는 sticky. startsAt/endsAt 미설정(구 모델 방)이면 저장 status 를 그대로 반환한다.
     * SELECTING 구간의 세부(접수/발표대기/공개)는 opensAt/closesAt/resultAt 시각으로 별도 파생한다.
     */
    public RoomStatus statusAt(LocalDateTime now) {
        if (status == RoomStatus.CANCELLED) {
            return RoomStatus.CANCELLED;
        }
        if (startsAt == null || endsAt == null) {
            return status; // 스케줄 시각이 없는 구 모델 방은 저장 status 를 신뢰
        }
        if (now.isBefore(startsAt)) {
            return RoomStatus.PENDING;
        }
        if (!now.isBefore(endsAt)) {
            return RoomStatus.ENDED;
        }
        if (finalSelectionOpensAt != null && !now.isBefore(finalSelectionOpensAt)) {
            return RoomStatus.SELECTING;
        }
        return RoomStatus.ACTIVE;
    }

    /**
     * reconciler 가 파생 상태를 저장 컬럼에 materialize 할 때 쓴다(#320).
     * <p>
     * 새 모델 방이 <b>진행 이후 상태</b>로 가면 startedAt 을 startsAt 으로 앵커한다. 평소엔
     * PENDING→ACTIVE 를 거치며 채워지지만, 배치가 오래 멈췄다 살아나면 PENDING→SELECTING/ENDED 로
     * <b>직행</b>할 수 있다. 그때 앵커가 비면 startedAt 을 전제하는 계산(dateOfDay·announcedAt)과
     * MatchResultService 의 "미시작" 가드가 깨져 결과 발표가 영영 숨는다.
     * <p>
     * 이미 앵커가 있으면 덮어쓰지 않고, 구 모델 방(startsAt=null)은 건드리지 않는다.
     */
    public Room materialized(RoomStatus derived) {
        boolean needsAnchor = startsAt != null && startedAt == null && derived != RoomStatus.PENDING;
        LocalDateTime anchor = needsAnchor ? startsAt : startedAt;
        return new Room(id, derived, scheduledDate, totalDays, anchor, endedAt,
                startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt);
    }

    /** 운영진 수동 취소(#311): → CANCELLED. sticky(이후 statusAt 도 CANCELLED). */
    public Room cancelled() {
        return new Room(id, RoomStatus.CANCELLED, scheduledDate, totalDays, startedAt, endedAt,
                startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt);
    }

    /** 방 시작: PENDING → ACTIVE, startedAt 앵커. currentDay는 startedAt에서 파생하므로 저장하지 않는다. */
    public Room started(LocalDateTime startedAt) {
        return new Room(id, RoomStatus.ACTIVE, scheduledDate, totalDays, startedAt, endedAt,
                startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt);
    }

    /** 세션 종료: ACTIVE → ENDED, endedAt 앵커. 종료 후 getLegacyActive()·findCurrent 조회에서 제외된다. */
    public Room ended(LocalDateTime endedAt) {
        return new Room(id, RoomStatus.ENDED, scheduledDate, totalDays, startedAt, endedAt,
                startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt);
    }

    /**
     * 현재 Day = min(totalDays, 1 + startedAt~now 경과일). 하루 경계 시각(dayStartHour, 예: 09:00)은
     * business 계층이 UploadSlot.dayStartHour()에서 읽어 주입한다(room 도메인이 mission에 결합되지 않도록).
     * 미시작(PENDING/startedAt 없음)이면 0. 별도 증분 잡 없이 시각에서 파생한다.
     */
    public int currentDayAt(LocalDateTime now, int dayStartHour) {
        // 새 모델(#320): Day 는 starts_at 상대 24시간 단위. dayStartHour(09:00) 경계를 쓰지 않는다
        // — 저녁 시작 방(예: 19:00)이 다음날 09:00에 Day2 가 되어버리는 문제를 없앤다.
        // 진행 중이 아닌 방(시작 전·종료·취소)은 구 모델과 동일하게 0.
        if (startsAt != null) {
            RoomStatus derived = statusAt(now);
            if (derived == RoomStatus.PENDING || derived == RoomStatus.ENDED || derived == RoomStatus.CANCELLED) {
                return 0;
            }
            return (int) Math.min(totalDays, 1 + ChronoUnit.DAYS.between(startsAt, now));
        }
        if (status != RoomStatus.ACTIVE || startedAt == null) {
            return 0;
        }
        return (int) Math.min(totalDays, rawDayAt(now, dayStartHour));
    }

    /**
     * 마지막 날(totalDays)이 지나 세션이 끝났는지. Day+1의 하루 경계(dayStartHour)를 넘긴 ACTIVE 방이 true.
     * currentDayAt은 totalDays로 캡되어 "경과"를 구분할 수 없으므로 캡 이전 raw day로 판정한다.
     */
    public boolean hasEndedBy(LocalDateTime now, int dayStartHour) {
        if (status != RoomStatus.ACTIVE || startedAt == null) {
            return false;
        }
        return rawDayAt(now, dayStartHour) > totalDays;
    }

    private long rawDayAt(LocalDateTime now, int dayStartHour) {
        long elapsedDays = ChronoUnit.DAYS.between(
                dateOfDay(1, dayStartHour),
                now.minusHours(dayStartHour).toLocalDate());
        return 1 + Math.max(0, elapsedDays);
    }

    /**
     * Day N의 달력 날짜. currentDayAt과 같은 앵커(startedAt - dayStartHour)에서 파생해
     * Day 판정과 특정 시각 판정(예: 최종선택 오픈)이 서로 다른 경계를 쓰지 않게 한다.
     * ACTIVE + startedAt 존재를 전제한다(호출측이 currentDayAt 게이트로 보장).
     */
    public LocalDate dateOfDay(int dayNumber, int dayStartHour) {
        // 새 모델(#320): Day 경계가 starts_at 상대 24시간이므로 날짜도 같은 앵커에서 파생한다.
        // startedAt 이 아직 비어 있는(전이 전) 방에서도 NPE 없이 계산돼야 한다 — 오늘 화면이 이 값을 쓴다.
        if (startsAt != null) {
            return startsAt.toLocalDate().plusDays(dayNumber - 1L);
        }
        return startedAt.minusHours(dayStartHour).toLocalDate().plusDays(dayNumber - 1L);
    }

    /**
     * 결과 발표 시각 = 마지막 날(totalDays)의 RESULT_ANNOUNCE_HOUR. 최종선택 마감·미매칭 확정(위로 구름)·
     * pending_result 노출이 모두 이 시각을 공유하므로 계산은 여기 한 곳에만 둔다.
     * dateOfDay와 같은 앵커에서 파생하며 startedAt 존재를 전제한다(호출측이 보장).
     */
    public LocalDateTime announcedAt(int dayStartHour) {
        // 새 모델(#320): 발표 시각은 어드민이 입력한 result_at 그 자체다(마지막 날 22:00 고정이 아니다).
        if (resultAt != null) {
            return resultAt;
        }
        return dateOfDay(totalDays, dayStartHour).atStartOfDay().plusHours(RESULT_ANNOUNCE_HOUR);
    }
}
