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
import org.son.haruharu.domain.mission.implement.RoomMissionSetting;
import org.son.haruharu.domain.mission.implement.RoomMissionSettingWriter;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotWriter;
import org.son.haruharu.domain.mission.implement.exception.InvalidRoomMissionSlotScheduleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoomMissionSlotServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 9, 1, 8, 0);
    private static final LocalDateTime SELECTION_OPENS_AT = STARTS_AT.plusDays(3);

    @Mock RoomMissionSlotWriter roomMissionSlotWriter;
    @Mock RoomMissionSettingWriter roomMissionSettingWriter;

    RoomMissionSlotService service;

    @BeforeEach
    void setUp() {
        service = new RoomMissionSlotService(roomMissionSlotWriter, roomMissionSettingWriter);
    }

    @Test
    @DisplayName("미션을 켜면 켜짐 설정과 입력한 Day별 오전·오후 슬롯을 검증해 저장한다")
    void givenMissionEnabledWithValidCommands_whenCreateForRoom_thenSavesSettingAndSlots() {
        givenSaveReturnsInput();

        List<RoomMissionSlot> result = service.createForRoom(
                ROOM_ID, true, 3, STARTS_AT, SELECTION_OPENS_AT, commands());

        assertThat(result).hasSize(6);
        verify(roomMissionSlotWriter).saveAll(result);
        verify(roomMissionSettingWriter).save(RoomMissionSetting.of(ROOM_ID, true));
    }

    @Test
    @DisplayName("미션을 켰는데 슬롯이 없으면(null·빈 목록) 예외이고 아무것도 저장하지 않는다")
    void givenMissionEnabledWithoutCommands_whenCreateForRoom_thenThrowsAndSavesNothing() {
        assertThatThrownBy(() -> service.createForRoom(
                ROOM_ID, true, 3, STARTS_AT, SELECTION_OPENS_AT, null))
                .isInstanceOf(InvalidRoomMissionSlotScheduleException.class);
        assertThatThrownBy(() -> service.createForRoom(
                ROOM_ID, true, 3, STARTS_AT, SELECTION_OPENS_AT, List.of()))
                .isInstanceOf(InvalidRoomMissionSlotScheduleException.class);

        verifyNoInteractions(roomMissionSlotWriter, roomMissionSettingWriter);
    }

    @Test
    @DisplayName("미션을 끄면 꺼짐 설정만 저장하고 슬롯은 만들지 않는다")
    void givenMissionDisabled_whenCreateForRoom_thenSavesDisabledSettingOnly() {
        List<RoomMissionSlot> withNull = service.createForRoom(
                ROOM_ID, false, 3, STARTS_AT, SELECTION_OPENS_AT, null);
        List<RoomMissionSlot> withEmpty = service.createForRoom(
                ROOM_ID + 1, false, 3, STARTS_AT, SELECTION_OPENS_AT, List.of());

        assertThat(withNull).isEmpty();
        assertThat(withEmpty).isEmpty();
        verify(roomMissionSettingWriter).save(RoomMissionSetting.of(ROOM_ID, false));
        verify(roomMissionSettingWriter).save(RoomMissionSetting.of(ROOM_ID + 1, false));
        verifyNoInteractions(roomMissionSlotWriter);
    }

    @Test
    @DisplayName("미션을 끄면서 슬롯을 보내면 모순된 입력이므로 예외이고 아무것도 저장하지 않는다")
    void givenMissionDisabledWithCommands_whenCreateForRoom_thenThrowsAndSavesNothing() {
        assertThatThrownBy(() -> service.createForRoom(
                ROOM_ID, false, 3, STARTS_AT, SELECTION_OPENS_AT, commands()))
                .isInstanceOf(InvalidRoomMissionSlotScheduleException.class);

        verifyNoInteractions(roomMissionSlotWriter, roomMissionSettingWriter);
    }

    @Test
    @DisplayName("기본 슬롯 생성은 미션을 켜고 방 시작 상대 오전·오후 슬롯을 totalDays만큼 저장한다")
    void givenRoomSchedule_whenCreateDefaults_thenSavesTwoSlotsPerDay() {
        givenSaveReturnsInput();

        List<RoomMissionSlot> result = service.createDefaultsForRoom(
                ROOM_ID, 3, STARTS_AT, SELECTION_OPENS_AT);

        assertThat(result).hasSize(6);
        assertThat(result).extracting(RoomMissionSlot::opensAt).containsExactly(
                STARTS_AT.plusHours(3), STARTS_AT.plusHours(9),
                STARTS_AT.plusHours(27), STARTS_AT.plusHours(33),
                STARTS_AT.plusHours(51), STARTS_AT.plusHours(57));
        verify(roomMissionSlotWriter).saveAll(result);
        verify(roomMissionSettingWriter).save(RoomMissionSetting.of(ROOM_ID, true));
    }

    private void givenSaveReturnsInput() {
        given(roomMissionSlotWriter.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
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
