package org.son.haruharu.domain.mission.business;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.business.command.CreateRoomMissionSlotCommand;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoomMissionSlotServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 9, 1, 8, 0);
    private static final LocalDateTime SELECTION_OPENS_AT = STARTS_AT.plusDays(3);

    @Mock RoomMissionSlotWriter roomMissionSlotWriter;

    RoomMissionSlotService service;

    @BeforeEach
    void setUp() {
        service = new RoomMissionSlotService(roomMissionSlotWriter);
        given(roomMissionSlotWriter.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("입력한 Day별 오전·오후 슬롯을 검증한 뒤 저장한다")
    void givenValidCommands_whenCreateForRoom_thenSavesValidatedSlots() {
        List<RoomMissionSlot> result = service.createForRoom(
                ROOM_ID, 3, STARTS_AT, SELECTION_OPENS_AT, commands());

        assertThat(result).hasSize(6);
        verify(roomMissionSlotWriter).saveAll(result);
    }

    @Test
    @DisplayName("기본 슬롯 생성은 방 시작 상대 오전·오후 슬롯을 totalDays만큼 저장한다")
    void givenRoomSchedule_whenCreateDefaults_thenSavesTwoSlotsPerDay() {
        List<RoomMissionSlot> result = service.createDefaultsForRoom(
                ROOM_ID, 3, STARTS_AT, SELECTION_OPENS_AT);

        assertThat(result).hasSize(6);
        assertThat(result).extracting(RoomMissionSlot::opensAt).containsExactly(
                STARTS_AT.plusHours(3), STARTS_AT.plusHours(9),
                STARTS_AT.plusHours(27), STARTS_AT.plusHours(33),
                STARTS_AT.plusHours(51), STARTS_AT.plusHours(57));
        verify(roomMissionSlotWriter).saveAll(result);
    }

    private List<CreateRoomMissionSlotCommand> commands() {
        return List.of(
                command(1, MissionPeriod.MORNING, 1, 6),
                command(1, MissionPeriod.AFTERNOON, 9, 14),
                command(2, MissionPeriod.MORNING, 25, 30),
                command(2, MissionPeriod.AFTERNOON, 33, 38),
                command(3, MissionPeriod.MORNING, 49, 54),
                command(3, MissionPeriod.AFTERNOON, 57, 62));
    }

    private CreateRoomMissionSlotCommand command(int dayNumber, MissionPeriod period,
                                                  int opensOffset, int closesOffset) {
        return new CreateRoomMissionSlotCommand(dayNumber, period,
                STARTS_AT.plusHours(opensOffset), STARTS_AT.plusHours(closesOffset));
    }
}
