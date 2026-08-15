package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.command.CreateRoomCommand;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.business.exception.RoomInvalidScheduleException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock RoomReader roomReader;
    @Mock RoomWriter roomWriter;
    @Mock RoomMemberReader roomMemberReader;

    RoomService roomService;

    // 2026-06-26 12:00 KST 고정 → startedAt 앵커에 따라 currentDay가 파생된다.
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 26, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomReader, roomWriter, roomMemberReader, clock);
    }

    @Test
    @DisplayName("유효한 스케줄로 방 생성 시 PENDING 방을 저장하고 roomId를 반환한다")
    void givenValidSchedule_whenCreateRoom_thenSavesAndReturnsId() {
        LocalDateTime starts = LocalDateTime.of(2026, 6, 26, 14, 0);
        CreateRoomCommand cmd = new CreateRoomCommand(
                starts, starts.plusDays(2).withHour(20), starts.plusDays(2).withHour(22),
                starts.plusDays(2).withHour(23), starts.plusDays(2).withHour(23).withMinute(30), 3);
        given(roomWriter.save(any())).willReturn(new Room(99L, RoomStatus.PENDING, starts.toLocalDate(), 3,
                null, null, false, null, cmd.startsAt(), cmd.finalSelectionOpensAt(),
                cmd.finalSelectionClosesAt(), cmd.resultAt(), cmd.endsAt()));

        Long id = roomService.createRoom(cmd);

        assertThat(id).isEqualTo(99L);
    }

    @Test
    @DisplayName("시각 순서가 어긋난 스케줄로 방 생성 시 RoomInvalidScheduleException")
    void givenMisorderedSchedule_whenCreateRoom_thenThrows() {
        LocalDateTime t = LocalDateTime.of(2026, 6, 26, 14, 0);
        // 선택오픈이 시작보다 앞 → 순서 위반
        CreateRoomCommand bad = new CreateRoomCommand(
                t, t.minusHours(1), t.plusHours(2), t.plusHours(3), t.plusHours(4), 3);

        assertThatThrownBy(() -> roomService.createRoom(bad))
                .isInstanceOf(RoomInvalidScheduleException.class);
    }

    @Test
    @DisplayName("종료되지 않은 방에 속한 유저가 조회 시 방 정보와 멤버 수를 반환한다")
    void givenActiveRoom_whenGetMyRoom_thenReturnsRoom() {
        Long userId = 1L;
        Long roomId = 10L;
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, LocalDateTime.now(), null);

        given(roomMemberReader.findByUserId(userId))
            .willReturn(List.of(new RoomMember(51L, roomId, userId, "MALE", null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(roomId))).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(
            new RoomMember(52L, roomId, userId, "MALE", null, LocalDateTime.now()),
            new RoomMember(53L, roomId, 2L, "FEMALE", null, LocalDateTime.now())
        ));

        RoomResponse result = roomService.getMyRoom(userId);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.memberCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("속한 방이 없으면 RoomNotFoundException이 발생한다")
    void givenNoRoom_whenGetMyRoom_thenThrows() {
        Long userId = 1L;
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of());
        given(roomReader.findCurrent(List.of())).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getMyRoom(userId))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    @DisplayName("방 멤버가 roomId로 조회 시 방 정보를 반환한다")
    void givenMember_whenGetRoom_thenReturnsRoom() {
        Long userId = 1L;
        Long roomId = 20L;
        // startedAt=전날 09:00 → clock(2026-06-26 12:00) 기준 currentDay=2로 파생
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 25), 3,
            LocalDateTime.of(2026, 6, 25, 9, 0), null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(54L, roomId, userId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(
            new RoomMember(55L, roomId, userId, "MALE", null, LocalDateTime.now())
        ));

        RoomResponse result = roomService.getRoom(roomId, userId);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.currentDay()).isEqualTo(2);
    }

    @Test
    @DisplayName("방 멤버가 아닌 유저가 roomId로 조회 시 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetRoom_thenThrows() {
        Long userId = 1L;
        Long roomId = 30L;
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> roomService.getRoom(roomId, userId))
            .isInstanceOf(NotRoomMemberException.class);
    }

    @Test
    @DisplayName("종료되지 않은 방이 있는 유저는 hasCurrentRoom이 true다")
    void givenCurrentRoom_whenHasCurrentRoom_thenTrue() {
        Long userId = 1L;
        Long roomId = 40L;
        given(roomMemberReader.findByUserId(userId))
            .willReturn(List.of(new RoomMember(56L, roomId, userId, "MALE", null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(roomId))).willReturn(Optional.of(
            new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, LocalDateTime.now(), null)));

        assertThat(roomService.hasCurrentRoom(userId)).isTrue();
    }

    @Test
    @DisplayName("과거 방만 있거나 방 이력이 없는 유저는 hasCurrentRoom이 false다")
    void givenNoCurrentRoom_whenHasCurrentRoom_thenFalse() {
        Long withEndedOnly = 1L;
        Long endedRoomId = 50L;
        given(roomMemberReader.findByUserId(withEndedOnly))
            .willReturn(List.of(new RoomMember(57L, endedRoomId, withEndedOnly, "MALE", null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(endedRoomId))).willReturn(Optional.empty());

        Long noHistory = 2L;
        given(roomMemberReader.findByUserId(noHistory)).willReturn(List.of());

        assertThat(roomService.hasCurrentRoom(withEndedOnly)).isFalse();
        assertThat(roomService.hasCurrentRoom(noHistory)).isFalse();
    }
}
