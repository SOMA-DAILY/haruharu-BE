package org.son.haruharu.domain.roomschedule.implement;

import org.son.haruharu.domain.roomschedule.storage.exception.ScheduleItemNotFoundException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

@Component
public class RoomScheduleItemReader {

    private final RoomScheduleItemRepository repository;

    public RoomScheduleItemReader(RoomScheduleItemRepository repository) {
        this.repository = repository;
    }

    /** 방에 속한 행. 다른 방 행이면 404. */
    public RoomScheduleItem getInRoom(Long roomId, Long itemId) {
        return repository.findById(itemId)
                .filter(item -> item.roomId().equals(roomId))
                .orElseThrow(ScheduleItemNotFoundException::new);
    }

    public java.util.Optional<RoomScheduleItem> findById(Long itemId) {
        return repository.findById(itemId);
    }

    public RoomScheduleItem getForUpdate(Long itemId) {
        return repository.findByIdForUpdate(itemId).orElseThrow(ScheduleItemNotFoundException::new);
    }

    /**
     * 편성으로 공개를 정하는 방인지(#457). 공개 행을 전부 취소한 방도 true — 취소한 항목이 옛 DAY3 규칙으로 열리면 안 된다.
     */
    public boolean hasRevealSchedule(Long roomId) {
        return repository.existsRevealRow(roomId);
    }

    public List<RoomScheduleItem> findByRoomId(Long roomId) {
        return repository.findByRoomId(roomId);
    }

    public List<RoomScheduleItem> findDue(LocalDateTime now, int limit) {
        return repository.findDue(now, limit);
    }

    public List<RoomScheduleItem> findSendingSince(LocalDateTime threshold, int limit) {
        return repository.findSendingSince(threshold, limit);
    }

    /**
     * 이 행보다 먼저 공개된 항목(같은 방, 취소 아님, run_at 이 더 이른 행 — 같은 시각이면 id 가 작은 행).
     * 발송 잡이 같은 항목의 PROFILE_REVEAL 을 두 번 보내지 않게 한다.
     */
    public Set<RevealField> findRevealedBefore(RoomScheduleItem item) {
        Set<RevealField> revealed = EnumSet.noneOf(RevealField.class);
        for (RoomScheduleItem other : repository.findRevealRows(item.roomId())) {
            boolean earlier = other.runAt().isBefore(item.runAt())
                    || (other.runAt().isEqual(item.runAt()) && other.id() < item.id());
            if (!other.id().equals(item.id()) && earlier) {
                revealed.addAll(other.revealFields());
            }
        }
        return revealed;
    }

    /**
     * 항목별 공개 시각(F2) = 취소가 아닌 행 중 그 항목을 가진 가장 이른 run_at. 잡이 못 보냈어도(플래그 OFF, EXPIRED)
     * 시각이 지나면 열린다. 편성 방인지(옛 DAY3 규칙 여부)는 hasRevealSchedule 로 따로 본다(취소 행 포함).
     */
    public Map<RevealField, LocalDateTime> findRevealTimes(Long roomId) {
        Map<RevealField, LocalDateTime> times = new EnumMap<>(RevealField.class);
        for (RoomScheduleItem item : repository.findRevealRows(roomId)) {
            for (RevealField field : item.revealFields()) {
                times.merge(field, item.runAt(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        return times;
    }
}
