package org.son.haruharu.domain.mission.types.pairchat.implement;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.son.haruharu.domain.chat.implement.PairChatSchedule;
import org.son.haruharu.domain.chat.implement.PairChatWindow;
import org.son.haruharu.domain.mission.common.implement.MissionStatus;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Component;

/**
 * 1:1 대화 시간(#540)을 PAIR_CHAT 미션 일정으로 계산한다. 채팅의 {@link PairChatSchedule} 포트 구현.
 * 취소되지 않은 미션만 보고, 미션 공통 상태 규칙(방 시작 전 SCHEDULED, 끝 시각 이후·방 종료 CLOSED)을 그대로 따른다.
 * 방 시작 전에 시작이 잡힌 시간은 방이 열릴 때부터 열린 것으로 본다.
 */
@Component
public class PairChatMissionSchedule implements PairChatSchedule {

    private final RoomMissionReader missions;
    private final RoomReader rooms;

    public PairChatMissionSchedule(RoomMissionReader missions, RoomReader rooms) {
        this.missions = missions;
        this.rooms = rooms;
    }

    @Override
    public PairChatWindow windowAt(Long roomId, LocalDateTime now) {
        Room room = rooms.findById(roomId).orElse(null);
        if (room == null) return PairChatWindow.none();
        RoomStatus roomStatus = room.statusAt(now);
        if (roomStatus == RoomStatus.CANCELLED || roomStatus == RoomStatus.ENDED) return PairChatWindow.none();

        List<RoomMission> windows = missions.findByRoomIdAndType(roomId, MissionType.PAIR_CHAT).stream()
                .filter(mission -> mission.canceledAt() == null)
                .toList();

        RoomMission open = windows.stream()
                .filter(mission -> mission.statusAt(now, room) == MissionStatus.OPEN)
                .min(Comparator.comparing(mission -> effectiveOpensAt(mission, room)))
                .orElse(null);
        if (open != null) {
            LocalDateTime opensAt = effectiveOpensAt(open, room);
            return PairChatWindow.open(opensAt, dayOf(room, opensAt), open.effectiveClosesAt(room));
        }

        return windows.stream()
                .filter(mission -> mission.statusAt(now, room) == MissionStatus.SCHEDULED)
                .filter(mission -> opensBeforeClose(mission, room))
                .min(Comparator.comparing(mission -> effectiveOpensAt(mission, room)))
                .map(mission -> {
                    LocalDateTime opensAt = effectiveOpensAt(mission, room);
                    return PairChatWindow.scheduled(opensAt, dayOf(room, opensAt), mission.effectiveClosesAt(room));
                })
                .orElse(PairChatWindow.none());
    }

    private static LocalDateTime effectiveOpensAt(RoomMission mission, Room room) {
        LocalDateTime roomStart = room.startsAt();
        return roomStart != null && mission.opensAt().isBefore(roomStart) ? roomStart : mission.opensAt();
    }

    /** 방 시작·종료로 잘린 뒤에도 실제로 열리는 구간이 남는지. */
    private static boolean opensBeforeClose(RoomMission mission, Room room) {
        LocalDateTime closesAt = mission.effectiveClosesAt(room);
        return closesAt == null || effectiveOpensAt(mission, room).isBefore(closesAt);
    }

    /** 방 시작일 기준 달력 일차(1부터). 시작 시각이 없는 구 모델 방은 알 수 없다(null). */
    private static Integer dayOf(Room room, LocalDateTime at) {
        if (room.startsAt() == null) return null;
        return (int) ChronoUnit.DAYS.between(room.startsAt().toLocalDate(), at.toLocalDate()) + 1;
    }
}
