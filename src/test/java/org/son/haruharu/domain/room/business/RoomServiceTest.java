package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;

    RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomReader, roomMemberReader);
    }

    @Test
    @DisplayName("종료되지 않은 방에 속한 유저가 조회 시 방 정보와 멤버 수를 반환한다")
    void givenActiveRoom_whenGetMyRoom_thenReturnsRoom() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, 1, LocalDateTime.now(), null);

        given(roomMemberReader.findByUserId(userId))
            .willReturn(List.of(new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", 0, LocalDateTime.now())));
        given(roomReader.findCurrent(List.of(roomId))).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(
            new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", 0, LocalDateTime.now()),
            new RoomMember(UUID.randomUUID(), roomId, UUID.randomUUID(), "FEMALE", 0, LocalDateTime.now())
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
}
