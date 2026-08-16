package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 세션 종료 유스케이스. 마지막 날이 지난 ACTIVE 방을 ENDED로 전환해 스케줄러 알림 대상에서 제외한다. */
@Service
public class RoomTerminator {

    private final RoomReader roomReader;
    private final RoomWriter roomWriter;

    public RoomTerminator(RoomReader roomReader, RoomWriter roomWriter) {
        this.roomReader = roomReader;
        this.roomWriter = roomWriter;
    }

    /**
     * 마지막 날 경과 ACTIVE 방을 모두 ENDED로 전환한다. 반환값은 종료된 방 수.
     * <p>
     * 초대코드 모델 방(startsAt != null)은 제외한다(#320) — 종료 기준이 startedAt+3일이 아니라
     * 어드민이 입력한 ends_at 이고, 그 전이는 RoomStatusReconciler 가 담당한다.
     */
    @Transactional
    public int terminate(LocalDateTime now, int dayStartHour) {
        List<Room> expired = roomReader.getLegacyActive().stream()
                .filter(room -> room.hasEndedBy(now, dayStartHour))
                .toList();
        expired.forEach(room -> roomWriter.save(room.ended(now)));
        return expired.size();
    }
}
