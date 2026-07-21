package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.DemoRoomResponse;
import org.son.haruharu.domain.room.business.exception.DemoRoomNotFoundException;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DemoRoomServiceTest {

    @Mock RoomReader roomReader;
    @Mock RoomWriter roomWriter;
    @Mock RoomMemberReader roomMemberReader;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock IdentityService identityService;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(ZonedDateTime.of(2026, 7, 20, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    DemoRoomService service;

    @BeforeEach
    void setUp() {
        service = new DemoRoomService(roomReader, roomWriter, roomMemberReader, roomMemberWriter, identityService, clock);
    }

    @Test
    @DisplayName("데모 방이 없으면 Day1 데모 방을 생성하고 호출자를 멤버로 추가한다")
    void givenNoRoom_whenCreateOrGet_thenCreatesDemoRoomAndMember() {
        Long userId = 42L;
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of());
        given(roomWriter.save(any())).willReturn(
                new Room(1L, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 20), 3,
                        LocalDateTime.of(2026, 7, 20, 9, 0), null, true, 1));
        given(identityService.findVerified(userId))
                .willReturn(Optional.of(new VerifiedIdentity("MALE", LocalDate.of(1995, 6, 15))));

        DemoRoomResponse result = service.createOrGet(userId);

        assertThat(result.roomId()).isEqualTo(1L);
        assertThat(result.day()).isEqualTo(1);
        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        then(roomWriter).should().save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().demo()).isTrue();
        assertThat(roomCaptor.getValue().demoDay()).isEqualTo(1);
        ArgumentCaptor<RoomMember> memberCaptor = ArgumentCaptor.forClass(RoomMember.class);
        then(roomMemberWriter).should().save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().userId()).isEqualTo(userId);
        assertThat(memberCaptor.getValue().gender()).isEqualTo("MALE");
    }

    @Test
    @DisplayName("이미 데모 방이 있으면 새로 만들지 않고 그대로 반환한다")
    void givenExistingRoom_whenCreateOrGet_thenReturnsExisting() {
        Long userId = 42L;
        Room existing = new Room(7L, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 20), 3,
                LocalDateTime.of(2026, 7, 20, 9, 0), null, true, 2);
        given(roomMemberReader.findByUserId(userId))
                .willReturn(List.of(RoomMember.of(7L, userId, "MALE", null, LocalDateTime.now(clock))));
        given(roomReader.findById(7L)).willReturn(Optional.of(existing));

        DemoRoomResponse result = service.createOrGet(userId);

        assertThat(result.roomId()).isEqualTo(7L);
        assertThat(result.day()).isEqualTo(2);
        then(roomWriter).should(never()).save(any());
        then(roomMemberWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("Day 진행 시 demoDay가 1 증가한다")
    void givenDemoRoom_whenAdvanceDay_thenBumpsDemoDay() {
        Long userId = 42L;
        Room room = new Room(7L, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 20), 3,
                LocalDateTime.of(2026, 7, 20, 9, 0), null, true, 1);
        given(roomMemberReader.findByUserId(userId))
                .willReturn(List.of(RoomMember.of(7L, userId, "MALE", null, LocalDateTime.now(clock))));
        given(roomReader.findById(7L)).willReturn(Optional.of(room));
        given(roomWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        DemoRoomResponse result = service.advanceDay(userId);

        assertThat(result.day()).isEqualTo(2);
    }

    @Test
    @DisplayName("Day 진행은 totalDays(3)를 넘지 않는다")
    void givenDay3_whenAdvanceDay_thenStaysAt3() {
        Long userId = 42L;
        Room room = new Room(7L, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 20), 3,
                LocalDateTime.of(2026, 7, 20, 9, 0), null, true, 3);
        given(roomMemberReader.findByUserId(userId))
                .willReturn(List.of(RoomMember.of(7L, userId, "MALE", null, LocalDateTime.now(clock))));
        given(roomReader.findById(7L)).willReturn(Optional.of(room));
        given(roomWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        DemoRoomResponse result = service.advanceDay(userId);

        assertThat(result.day()).isEqualTo(3);
    }

    @Test
    @DisplayName("데모 방이 없는데 Day 진행 시 DemoRoomNotFoundException(404)")
    void givenNoRoom_whenAdvanceDay_thenThrows() {
        Long userId = 42L;
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of());

        assertThatThrownBy(() -> service.advanceDay(userId))
                .isInstanceOf(DemoRoomNotFoundException.class);
    }
}
