package org.son.haruharu.domain.mission.business;

import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.domain.mission.business.command.CreateRoomMissionSlotCommand;
import org.son.haruharu.domain.mission.implement.RoomMissionSetting;
import org.son.haruharu.domain.mission.implement.RoomMissionSettingWriter;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotSchedule;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotWriter;
import org.son.haruharu.domain.mission.implement.exception.InvalidRoomMissionSlotScheduleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomMissionSlotService {

    private final RoomMissionSlotWriter roomMissionSlotWriter;
    private final RoomMissionSettingWriter roomMissionSettingWriter;

    public RoomMissionSlotService(RoomMissionSlotWriter roomMissionSlotWriter,
                                  RoomMissionSettingWriter roomMissionSettingWriter) {
        this.roomMissionSlotWriter = roomMissionSlotWriter;
        this.roomMissionSettingWriter = roomMissionSettingWriter;
    }

    /**
     * 방 생성 시 미션 사용 여부와 슬롯을 저장한다(#449). 켜면 total_days × 2 슬롯이 필수이고,
     * 끄면 슬롯을 받지 않으며 꺼짐 설정만 남긴다. 둘이 어긋나면 모순된 입력으로 거절한다.
     */
    @Transactional
    public List<RoomMissionSlot> createForRoom(Long roomId, boolean missionEnabled,
                                                int totalDays, LocalDateTime startsAt,
                                                LocalDateTime finalSelectionOpensAt,
                                                List<CreateRoomMissionSlotCommand> commands) {
        if (!missionEnabled) {
            if (commands != null && !commands.isEmpty()) {
                throw new InvalidRoomMissionSlotScheduleException();
            }
            roomMissionSettingWriter.save(RoomMissionSetting.of(roomId, false));
            return List.of();
        }
        if (commands == null || commands.stream().anyMatch(command -> command == null)) {
            throw new InvalidRoomMissionSlotScheduleException();
        }
        List<RoomMissionSlot> slots = commands.stream()
                .map(command -> RoomMissionSlot.of(roomId, command.dayNumber(), command.period(),
                        command.opensAt(), command.closesAt()))
                .toList();
        List<RoomMissionSlot> validated =
                RoomMissionSlotSchedule.validate(slots, totalDays, startsAt, finalSelectionOpensAt);
        roomMissionSettingWriter.save(RoomMissionSetting.of(roomId, true));
        return roomMissionSlotWriter.saveAll(validated);
    }

    /** 데모·개발 방용. 미션을 켜고 방 시작 상대 기본 슬롯을 만든다. */
    @Transactional
    public List<RoomMissionSlot> createDefaultsForRoom(Long roomId, int totalDays, LocalDateTime startsAt,
                                                        LocalDateTime finalSelectionOpensAt) {
        List<RoomMissionSlot> defaults =
                RoomMissionSlotSchedule.defaults(roomId, totalDays, startsAt, finalSelectionOpensAt);
        roomMissionSettingWriter.save(RoomMissionSetting.of(roomId, true));
        return roomMissionSlotWriter.saveAll(defaults);
    }
}
