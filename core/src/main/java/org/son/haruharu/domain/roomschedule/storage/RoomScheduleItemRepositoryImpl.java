package org.son.haruharu.domain.roomschedule.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.roomschedule.implement.RevealField;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItem;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItemRepository;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleStatus;
import org.son.haruharu.domain.roomschedule.implement.ScheduleContent;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.son.haruharu.domain.roomschedule.implement.RoomScheduleStatus.*;

@Repository
@RequiredArgsConstructor
public class RoomScheduleItemRepositoryImpl implements RoomScheduleItemRepository {

    private final RoomScheduleItemJpaRepository jpaRepository;

    @Override
    public RoomScheduleItem save(RoomScheduleItem item) {
        return jpaRepository.save(RoomScheduleItemEntity.from(item)).toDomain();
    }

    @Override
    public Optional<RoomScheduleItem> findById(Long id) {
        return jpaRepository.findById(id).map(RoomScheduleItemEntity::toDomain);
    }

    @Override
    public List<RoomScheduleItem> findByRoomId(Long roomId) {
        return jpaRepository.findByRoomIdOrderByRunAtAscIdAsc(roomId).stream()
                .map(RoomScheduleItemEntity::toDomain).toList();
    }

    @Override
    public int updateIfScheduled(Long id, Long roomId, LocalDateTime runAt, ScheduleContent content, String actor,
                                 LocalDateTime now) {
        return jpaRepository.updateIfScheduled(id, roomId, runAt, RevealField.join(content.revealFields()),
                content.messageContent(), content.push(), content.pushTitle(), content.pushBody(), actor, now,
                SCHEDULED);
    }

    @Override
    public int rescheduleIfScheduled(Long id, Long roomId, LocalDateTime runAt, String actor, LocalDateTime now) {
        return jpaRepository.reschedule(id, roomId, runAt, actor, now, SCHEDULED);
    }

    @Override
    public int cancelIfScheduled(Long id, Long roomId, String actor, LocalDateTime now) {
        return jpaRepository.transitionInRoom(id, roomId, SCHEDULED, CANCELED, actor, now);
    }

    @Override
    public int claim(Long id, LocalDateTime now) {
        return jpaRepository.transition(id, SCHEDULED, SENDING, now);
    }

    @Override
    public int markSent(Long id, Long sentMessageId, LocalDateTime now) {
        return jpaRepository.markSent(id, sentMessageId, now, SENDING, SENT);
    }

    @Override
    public int markFailed(Long id, RoomScheduleStatus from, String reason, LocalDateTime now) {
        return jpaRepository.markFailed(id, reason, now, from, FAILED);
    }

    @Override
    public List<RoomScheduleItem> findDue(LocalDateTime now, int limit) {
        return jpaRepository.findDue(SCHEDULED, now, PageRequest.of(0, limit)).stream()
                .map(RoomScheduleItemEntity::toDomain).toList();
    }

    @Override
    public List<RoomScheduleItem> findSendingSince(LocalDateTime threshold, int limit) {
        return jpaRepository.findStatusUpdatedBefore(SENDING, threshold, PageRequest.of(0, limit)).stream()
                .map(RoomScheduleItemEntity::toDomain).toList();
    }

    @Override
    public List<RoomScheduleItem> findRevealRows(Long roomId) {
        return jpaRepository.findRevealRows(roomId, CANCELED).stream().map(RoomScheduleItemEntity::toDomain).toList();
    }
}
