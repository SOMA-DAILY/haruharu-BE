package org.son.haruharu.domain.roomschedule.implement;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RoomScheduleItemWriter {

    private final RoomScheduleItemRepository repository;

    public RoomScheduleItemWriter(RoomScheduleItemRepository repository) {
        this.repository = repository;
    }

    public RoomScheduleItem save(RoomScheduleItem item) {
        return repository.save(item);
    }

    public boolean updateIfScheduled(Long id, Long roomId, LocalDateTime runAt, ScheduleContent content,
                                     String actor, LocalDateTime now) {
        return repository.updateIfScheduled(id, roomId, runAt, content, actor, now) == 1;
    }

    public boolean rescheduleIfScheduled(Long id, Long roomId, LocalDateTime runAt, String actor, LocalDateTime now) {
        return repository.rescheduleIfScheduled(id, roomId, runAt, actor, now) == 1;
    }

    public boolean cancelIfScheduled(Long id, Long roomId, String actor, LocalDateTime now) {
        return repository.cancelIfScheduled(id, roomId, actor, now) == 1;
    }

    public boolean claim(Long id, LocalDateTime now) {
        return repository.claim(id, now) == 1;
    }

    public boolean markSent(Long id, Long sentMessageId, LocalDateTime now) {
        return repository.markSent(id, sentMessageId, now) == 1;
    }

    public boolean markFailed(Long id, RoomScheduleStatus from, String reason, LocalDateTime now) {
        return repository.markFailed(id, from, reason, now) == 1;
    }

    public boolean markFailedIfUnchanged(RoomScheduleItem snapshot, String reason, LocalDateTime now) {
        return repository.markFailedIfUnchanged(snapshot, reason, now) == 1;
    }
}
