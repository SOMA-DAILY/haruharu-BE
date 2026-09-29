package org.son.haruharu.domain.roomschedule.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 편성 행 포트(#457). 상태 전이는 전부 "현재 상태가 X 인 행만" 조건부 UPDATE 로 하고 바뀐 행 수를 돌려준다
 * — 어드민 수정과 잡의 claim, 인스턴스 2개의 claim 이 같은 조건으로 경쟁해 한쪽만 이긴다.
 */
public interface RoomScheduleItemRepository {
    RoomScheduleItem save(RoomScheduleItem item);

    Optional<RoomScheduleItem> findById(Long id);

    /** run_at, id 순. */
    List<RoomScheduleItem> findByRoomId(Long roomId);

    int updateIfScheduled(Long id, Long roomId, LocalDateTime runAt, ScheduleContent content, String actor,
                          LocalDateTime now);

    int rescheduleIfScheduled(Long id, Long roomId, LocalDateTime runAt, String actor, LocalDateTime now);

    int cancelIfScheduled(Long id, Long roomId, String actor, LocalDateTime now);

    /** SCHEDULED 이고 run_at <= now 인 행만 SENDING. 1 이면 이 인스턴스가 보낼 차례. */
    int claim(Long id, LocalDateTime now);

    /** SENDING → SENT. */
    int markSent(Long id, Long sentMessageId, LocalDateTime now);

    /** from → FAILED(reason). */
    int markFailed(Long id, RoomScheduleStatus from, String reason, LocalDateTime now);

    /** status=SCHEDULED and run_at <= now, 오래된 순. */
    List<RoomScheduleItem> findDue(LocalDateTime now, int limit);

    /** status=SENDING 인 채 updated_at 이 threshold 이전(크래시로 멈춘 행). */
    List<RoomScheduleItem> findSendingSince(LocalDateTime threshold, int limit);

    /** 공개 판정용: 취소가 아니고 공개 항목이 있는 행. */
    List<RoomScheduleItem> findRevealRows(Long roomId);

    /** 공개 항목이 있는 행이 하나라도 있는지(취소 포함). 없으면 편성 없는 옛 방(DAY3 규칙). */
    boolean existsRevealRow(Long roomId);
}
