package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 방 시작 유스케이스. 09:00에 시작 예정(PENDING) 방을 ACTIVE로 전환한다.
 * scheduledDate <= 기준일 조회라서, 09:00 실행을 놓쳐도(배포·장애) 다음 실행이 지난 방을 회수해 시작시킨다(#170).
 * 회수된 방의 startedAt은 실제 활성화 시각으로 앵커되므로 3일 세션이 온전히 보장된다.
 */
@Service
public class RoomActivator {

    private final RoomReader roomReader;
    private final RoomWriter roomWriter;
    private final ApplicationEventPublisher eventPublisher;

    public RoomActivator(RoomReader roomReader, RoomWriter roomWriter,
                         ApplicationEventPublisher eventPublisher) {
        this.roomReader = roomReader;
        this.roomWriter = roomWriter;
        this.eventPublisher = eventPublisher;
    }

    /** 기준일 이전(포함) 시작 예정인 PENDING 방을 모두 시작시킨다. 반환값은 시작된 방 수. */
    @Transactional
    public int activate(LocalDate scheduledDate, LocalDateTime startedAt) {
        List<Room> pending = roomReader.getPendingScheduledOnOrBefore(scheduledDate);
        pending.forEach(room -> {
            roomWriter.save(room.started(startedAt));
            eventPublisher.publishEvent(new RoomStartedEvent(room.id()));
        });
        return pending.size();
    }
}
