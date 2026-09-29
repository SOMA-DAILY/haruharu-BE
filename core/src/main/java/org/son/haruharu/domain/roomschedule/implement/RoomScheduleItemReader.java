package org.son.haruharu.domain.roomschedule.implement;

import org.son.haruharu.domain.roomschedule.storage.exception.ScheduleItemNotFoundException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
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
     * 항목별 공개 시각(F2) = 취소가 아닌 행 중 그 항목을 가진 가장 이른 run_at. 잡이 못 보냈어도(플래그 OFF, EXPIRED)
     * 시각이 지나면 열린다. 편성에 공개 행이 하나도 없으면 빈 맵(옛 방 → 기존 DAY3 규칙).
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
