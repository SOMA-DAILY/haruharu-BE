package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomWriter;
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

    public RoomActivator(RoomReader roomReader, RoomWriter roomWriter) {
        this.roomReader = roomReader;
        this.roomWriter = roomWriter;
    }

    /** scheduledDate 당일 시작 예정(PENDING) 방을 모두 시작시킨다. 반환값은 시작된 방 수. */
    @Transactional
    public int activate(LocalDate scheduledDate, LocalDateTime startedAt) {
        List<Room> pending = roomReader.getScheduledPending(scheduledDate);
        pending.forEach(room -> roomWriter.save(room.started(startedAt)));
        return pending.size();
    }
}
