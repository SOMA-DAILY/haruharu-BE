package org.son.haruharu.domain.mission.types.pairchat.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.implement.PairChatWindow;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PairChatMissionScheduleTest {
    // 방: 10/28 09:00 시작, 10/30 21:00 종료(3일 방). 1일차 20:00~22:00, 2일차 20:00~22:00 대화 시간.
    final LocalDateTime starts = LocalDateTime.of(2026, 10, 28, 9, 0);
    final Room room = Room.createScheduled(starts, starts.plusDays(2).plusHours(9), starts.plusDays(2).plusHours(10),
            starts.plusDays(2).plusHours(11), starts.plusDays(2).plusHours(12), 3);
    final RoomReader rooms = mock(RoomReader.class);
    final RoomMissionReader missions = mock(RoomMissionReader.class);
    final PairChatMissionSchedule schedule = new PairChatMissionSchedule(missions, rooms);

    RoomMission window(long id, LocalDateTime opensAt, LocalDateTime closesAt, LocalDateTime canceledAt) {
        return new RoomMission(id, 1L, MissionType.PAIR_CHAT, new PairChatMissionDefinition(), null,
                opensAt, closesAt, canceledAt, "admin:1", "admin:1", starts.minusDays(1), starts.minusDays(1));
    }

    void given(RoomMission... windows) {
        when(rooms.findById(1L)).thenReturn(Optional.of(room));
        when(missions.findByRoomIdAndType(1L, MissionType.PAIR_CHAT)).thenReturn(List.of(windows));
    }

    final LocalDateTime day1At20 = LocalDateTime.of(2026, 10, 28, 20, 0);
    final LocalDateTime day2At20 = LocalDateTime.of(2026, 10, 29, 20, 0);

    @Test
    @DisplayName("대화 시간 전이면 다음 시간의 시작·끝과 방 일차를 알려 준다")
    void givenBeforeWindow_whenWindowAt_thenScheduledWithDay() {
        given(window(2L, day2At20, day2At20.plusHours(2), null), window(1L, day1At20, day1At20.plusHours(2), null));

        PairChatWindow result = schedule.windowAt(1L, day1At20.minusMinutes(1));

        assertThat(result).isEqualTo(PairChatWindow.scheduled(day1At20, 1, day1At20.plusHours(2)));
    }

    @Test
    @DisplayName("대화 시간 중이면 열림과 이번 시간의 끝을 알려 준다(시작 시각 포함, 끝 시각 제외)")
    void givenInsideWindow_whenWindowAt_thenOpen() {
        given(window(1L, day1At20, day1At20.plusHours(2), null), window(2L, day2At20, day2At20.plusHours(2), null));

        assertThat(schedule.windowAt(1L, day1At20)).isEqualTo(PairChatWindow.open(day1At20, 1, day1At20.plusHours(2)));
        assertThat(schedule.windowAt(1L, day1At20.plusHours(2).minusNanos(1000)).isOpen()).isTrue();
        // 끝 시각이 되면 닫히고 다음 시간(2일차)을 알려 준다.
        assertThat(schedule.windowAt(1L, day1At20.plusHours(2)))
                .isEqualTo(PairChatWindow.scheduled(day2At20, 2, day2At20.plusHours(2)));
    }

    @Test
    @DisplayName("마지막 대화 시간이 끝났거나 예정이 없으면 NONE")
    void givenNoRemainingWindow_whenWindowAt_thenNone() {
        given(window(1L, day1At20, day1At20.plusHours(2), null));
        assertThat(schedule.windowAt(1L, day1At20.plusHours(3))).isEqualTo(PairChatWindow.none());

        given();
        assertThat(schedule.windowAt(1L, day1At20)).isEqualTo(PairChatWindow.none());
    }

    @Test
    @DisplayName("취소된 대화 시간은 열리지도 예정으로 보이지도 않는다")
    void givenCanceledWindow_whenWindowAt_thenIgnored() {
        given(window(1L, day1At20, day1At20.plusHours(2), day1At20.minusHours(1)));
        assertThat(schedule.windowAt(1L, day1At20.plusMinutes(10))).isEqualTo(PairChatWindow.none());
    }

    @Test
    @DisplayName("방이 끝난 뒤나 없는 방이면 NONE")
    void givenEndedOrMissingRoom_whenWindowAt_thenNone() {
        given(window(1L, day1At20, day1At20.plusHours(2), null));
        assertThat(schedule.windowAt(1L, starts.plusDays(3))).isEqualTo(PairChatWindow.none());

        when(rooms.findById(2L)).thenReturn(Optional.empty());
        assertThat(schedule.windowAt(2L, day1At20)).isEqualTo(PairChatWindow.none());
    }

    @Test
    @DisplayName("방 종료 시각이나 그 뒤에 시작하는 대화 시간은 열릴 구간이 없어 예정(SCHEDULED)이 아니라 NONE")
    void givenWindowStartingAtOrAfterRoomEnd_whenWindowAt_thenNone() {
        LocalDateTime roomEnd = room.endsAt();
        given(window(1L, roomEnd, roomEnd.plusHours(1), null), window(2L, roomEnd.plusHours(1), roomEnd.plusHours(2), null));

        assertThat(schedule.windowAt(1L, day1At20)).isEqualTo(PairChatWindow.none());
        assertThat(schedule.windowAt(1L, roomEnd.minusMinutes(1))).isEqualTo(PairChatWindow.none());
    }

    @Test
    @DisplayName("방 시작 전에 시작 시각이 잡힌 대화 시간은 방이 열릴 때부터 열린 것으로 본다")
    void givenWindowStartingBeforeRoom_whenWindowAt_thenEffectiveStartIsRoomStart() {
        given(window(1L, starts.minusHours(1), starts.plusHours(1), null));

        assertThat(schedule.windowAt(1L, starts.minusMinutes(30)))
                .isEqualTo(PairChatWindow.scheduled(starts, 1, starts.plusHours(1)));
        assertThat(schedule.windowAt(1L, starts)).isEqualTo(PairChatWindow.open(starts, 1, starts.plusHours(1)));
    }
}
