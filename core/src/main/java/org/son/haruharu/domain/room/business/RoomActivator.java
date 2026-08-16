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

/** 방 시작 유스케이스. scheduledDate 당일 09:00에 PENDING 방을 ACTIVE로 전환한다. */
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

    /**
     * scheduledDate 당일 시작 예정(PENDING) 방을 모두 시작시킨다. 반환값은 시작된 방 수.
     * <p>
     * 초대코드 모델 방은 <b>조회 단계</b>(getScheduledPending)에서 제외된다(#320) — scheduledDate 가
     * startsAt 의 날짜로 채워져 있어 그대로 두면 19:00 시작 예정 방이 09:00 에 조기 flip 되고 앵커도 어긋난다.
     * 필터를 호출부마다 흩뿌리지 않고 조회에 두어 누락을 구조적으로 막는다.
     */
    @Transactional
    public int activate(LocalDate scheduledDate, LocalDateTime startedAt) {
        List<Room> pending = roomReader.getScheduledPending(scheduledDate);
        pending.forEach(room -> {
            roomWriter.save(room.started(startedAt));
            eventPublisher.publishEvent(new RoomStartedEvent(room.id()));
        });
        return pending.size();
    }
}
