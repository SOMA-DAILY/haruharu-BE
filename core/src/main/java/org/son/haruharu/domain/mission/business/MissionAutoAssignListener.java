package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 소개팅방 시작(RoomStartedEvent) 커밋 후 방별 미션 인스턴스를 자동 배정한다.
 * 방 활성화 트랜잭션이 미션 배정 실패로 롤백되지 않도록 AFTER_COMMIT에서 처리한다.
 * room_id 당 1회 멱등이라 재발행에도 안전하다(GROUP 채팅 자동 생성과 동일 패턴).
 */
@Component
public class MissionAutoAssignListener {

    private final RoomReader roomReader;
    private final RoomMissionService roomMissionService;

    public MissionAutoAssignListener(RoomReader roomReader, RoomMissionService roomMissionService) {
        this.roomReader = roomReader;
        this.roomMissionService = roomMissionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRoomStarted(RoomStartedEvent event) {
        roomReader.findById(event.roomId())
                .ifPresent(roomMissionService::assignForRoom);
    }
}
