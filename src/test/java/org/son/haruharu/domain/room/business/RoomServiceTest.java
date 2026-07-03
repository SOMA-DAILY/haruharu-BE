package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;

    RoomService roomService;

    // 2026-06-26 12:00 KST 고정 → startedAt 앵커에 따라 currentDay가 파생된다.
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 26, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomReader, roomMemberReader, clock);
    }

    @Test
    @DisplayName("종료되지 않은 방에 속한 유저가 조회 시 방 정보와 멤버 수를 반환한다")
    void givenActiveRoom_whenGetMyRoom_thenReturnsRoom() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, LocalDateTime.now(), null);

        given(roomMemberReader.findByUserId(userId))
            .willReturn(List.of(new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(roomId))).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(
            new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", null, LocalDateTime.now()),
            new RoomMember(UUID.randomUUID(), roomId, UUID.randomUUID(), "FEMALE", null, LocalDateTime.now())
        ));

        RoomResponse result = roomService.getMyRoom(userId);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.memberCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("속한 방이 없으면 RoomNotFoundException이 발생한다")
    void givenNoRoom_whenGetMyRoom_thenThrows() {
        UUID userId = UUID.randomUUID();
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of());
        given(roomReader.findCurrent(List.of())).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getMyRoom(userId))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    @DisplayName("방 멤버가 roomId로 조회 시 방 정보를 반환한다")
    void givenMember_whenGetRoom_thenReturnsRoom() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        // startedAt=전날 09:00 → clock(2026-06-26 12:00) 기준 currentDay=2로 파생
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 25), 3,
            LocalDateTime.of(2026, 6, 25, 9, 0), null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(
            new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", null, LocalDateTime.now())
        ));

        RoomResponse result = roomService.getRoom(roomId, userId);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.currentDay()).isEqualTo(2);
    }

    @Test
    @DisplayName("방 멤버가 아닌 유저가 roomId로 조회 시 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetRoom_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> roomService.getRoom(roomId, userId))
            .isInstanceOf(NotRoomMemberException.class);
    }

    @Test
    @DisplayName("종료되지 않은 방이 있는 유저는 hasCurrentRoom이 true다")
    void givenCurrentRoom_whenHasCurrentRoom_thenTrue() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        given(roomMemberReader.findByUserId(userId))
            .willReturn(List.of(new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(roomId))).willReturn(Optional.of(
            new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, LocalDateTime.now(), null)));

        assertThat(roomService.hasCurrentRoom(userId)).isTrue();
    }

    @Test
    @DisplayName("과거 방만 있거나 방 이력이 없는 유저는 hasCurrentRoom이 false다")
    void givenNoCurrentRoom_whenHasCurrentRoom_thenFalse() {
        UUID withEndedOnly = UUID.randomUUID();
        UUID endedRoomId = UUID.randomUUID();
        given(roomMemberReader.findByUserId(withEndedOnly))
            .willReturn(List.of(new RoomMember(UUID.randomUUID(), endedRoomId, withEndedOnly, "MALE", null, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(endedRoomId))).willReturn(Optional.empty());

        UUID noHistory = UUID.randomUUID();
        given(roomMemberReader.findByUserId(noHistory)).willReturn(List.of());

        assertThat(roomService.hasCurrentRoom(withEndedOnly)).isFalse();
        assertThat(roomService.hasCurrentRoom(noHistory)).isFalse();
    }
}
