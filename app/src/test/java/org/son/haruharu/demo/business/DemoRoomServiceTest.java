package org.son.haruharu.demo.business;

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.user.implement.UserReader;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoRoomServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long DEMO_USER_ID = 1L;

    @Mock UserReader userReader;
    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock RoomWriter roomWriter;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock DemoGhostProvisioner demoGhostProvisioner;
    @Mock DemoRoomCleaner demoRoomCleaner;
    @Mock ApplicationEventPublisher eventPublisher;

    DemoRoomService service;

    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 24, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @BeforeEach
    void setUp() {
        service = new DemoRoomService(userReader, roomReader, roomMemberReader, roomWriter, roomMemberWriter,
                demoGhostProvisioner, demoRoomCleaner, eventPublisher, clock);
    }

    private RoomMember memberInRoom(Long roomId) {
        return new RoomMember(9L, roomId, DEMO_USER_ID, "MALE", null, LocalDateTime.now());
    }

    private Room anyRoom() {
        LocalDateTime t = LocalDateTime.of(2026, 6, 24, 10, 0);
        return new Room(100L, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, t, null,
                t, t.plusDays(3), t.plusDays(3).plusHours(1), t.plusDays(3).plusHours(2), t.plusDays(3).plusHours(3));
    }

    private void stubBuild() {
        given(roomWriter.save(any())).willReturn(anyRoom());
        given(demoGhostProvisioner.ensureGhosts()).willReturn(List.of(
                new DemoGhost(2L, "MALE"), new DemoGhost(3L, "MALE"),
                new DemoGhost(4L, "FEMALE"), new DemoGhost(5L, "FEMALE"), new DemoGhost(6L, "FEMALE")));
    }

    @Test
    @DisplayName("현재 데모 방이 있으면 재사용하고 새로 만들지 않는다")
    void givenCurrentRoom_whenRebuild_thenReuses() {
        given(roomMemberReader.findByUserId(DEMO_USER_ID)).willReturn(List.of(memberInRoom(50L)));
        given(roomReader.findCurrent(eq(List.of(50L)), any())).willReturn(Optional.of(anyRoom()));

        service.rebuildIfStale(DEMO_USER_ID);

        verify(userReader).lockById(DEMO_USER_ID); // 직렬화 락
        verify(roomWriter, never()).save(any());
        verify(demoRoomCleaner, never()).deleteRoomCascade(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("현재 방이 없으면 새 방·유령 6인 멤버를 만들고 RoomStartedEvent를 발행한다")
    void givenNoCurrentRoom_whenRebuild_thenBuildsAndPublishes() {
        given(roomMemberReader.findByUserId(DEMO_USER_ID)).willReturn(List.of());
        stubBuild();

        service.rebuildIfStale(DEMO_USER_ID);

        // 데모 + 유령 5 = 6명
        ArgumentCaptor<List<RoomMember>> captor = ArgumentCaptor.forClass(List.class);
        verify(roomMemberWriter).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(6);
        assertThat(captor.getValue()).extracting(RoomMember::gender)
                .containsExactly("MALE", "MALE", "MALE", "FEMALE", "FEMALE", "FEMALE");

        ArgumentCaptor<RoomStartedEvent> eventCaptor = ArgumentCaptor.forClass(RoomStartedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().roomId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("현재 방이 없으면 종료·잔존 데모 방을 먼저 물리 삭제한다(누적 방지)")
    void givenEndedRoom_whenRebuild_thenCleansOldFirst() {
        given(roomMemberReader.findByUserId(DEMO_USER_ID)).willReturn(List.of(memberInRoom(50L)));
        given(roomReader.findCurrent(eq(List.of(50L)), any())).willReturn(Optional.empty());
        stubBuild();

        service.rebuildIfStale(DEMO_USER_ID);

        verify(demoRoomCleaner).deleteRoomCascade(50L);
        verify(roomWriter).save(any()); // 정리 후 새로 생성
    }

    @Test
    @DisplayName("리셋은 데모 유저의 모든 방을 물리 삭제한다")
    void whenReset_thenCleansAllDemoRooms() {
        given(roomMemberReader.findByUserId(DEMO_USER_ID))
                .willReturn(List.of(memberInRoom(50L), memberInRoom(51L)));

        service.reset(DEMO_USER_ID);

        verify(userReader).lockById(DEMO_USER_ID);
        verify(demoRoomCleaner).deleteRoomCascade(50L);
        verify(demoRoomCleaner).deleteRoomCascade(51L);
        verify(roomWriter, never()).save(any());
    }
}
