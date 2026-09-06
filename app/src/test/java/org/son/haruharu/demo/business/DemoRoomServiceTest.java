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
import org.son.haruharu.domain.room.business.event.MutualMatchEvent;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.MatchResultWriter;
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
import static org.mockito.ArgumentMatchers.argThat;
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
    @Mock DemoVideoSeeder demoVideoSeeder;
    @Mock MatchResultReader matchResultReader;
    @Mock MatchResultWriter matchResultWriter;
    @Mock RoomMissionSlotService roomMissionSlotService;
    @Mock ApplicationEventPublisher eventPublisher;

    DemoRoomService service;

    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 24, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @BeforeEach
    void setUp() {
        service = new DemoRoomService(userReader, roomReader, roomMemberReader, roomWriter, roomMemberWriter,
                demoGhostProvisioner, demoRoomCleaner, demoVideoSeeder, matchResultReader, matchResultWriter,
                roomMissionSlotService, eventPublisher, clock);
    }

    private RoomMember memberInRoom(Long roomId) {
        return new RoomMember(9L, roomId, DEMO_USER_ID, "MALE", null, null, LocalDateTime.now());
    }

    private Room anyRoom() {
        LocalDateTime t = LocalDateTime.of(2026, 6, 24, 10, 0);
        return new Room(100L, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, t, null,
                t, t.plusDays(3), t.plusDays(3).plusHours(1), t.plusDays(3).plusHours(2), t.plusDays(3).plusHours(3));
    }

    private void stubBuild() {
        given(roomWriter.save(any())).willReturn(anyRoom());
        given(roomMemberWriter.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0)); // 저장된 멤버를 시더로 전달
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
        verify(demoVideoSeeder, never()).seed(anyList(), any(), any());
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
        assertThat(captor.getValue()).extracting(RoomMember::pseudonym)
                .containsExactly("민준", "정석", "현우", "유진", "지우", "서연");

        ArgumentCaptor<RoomStartedEvent> eventCaptor = ArgumentCaptor.forClass(RoomStartedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().roomId()).isEqualTo(100L);

        verify(demoVideoSeeder).seed(anyList(), any(), any()); // 피드 영상 시드
        verify(roomMissionSlotService).createDefaultsForRoom(
                eq(100L), eq(3), eq(LocalDateTime.of(2026, 6, 24, 10, 0)),
                eq(LocalDateTime.of(2026, 6, 27, 10, 0)));
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

    // ── forceMatch (PR4) ────────────────────────────────────────────────────

    private RoomMember demoMember() {
        return new RoomMember(9L, 50L, DEMO_USER_ID, "MALE", null, null, LocalDateTime.now());
    }

    private RoomMember ghostMember(Long memberId, Long userId, String gender) {
        return new RoomMember(memberId, 50L, userId, gender, null, null, LocalDateTime.now());
    }

    private void stubCurrentRoomWithMembers(List<RoomMember> members) {
        given(roomMemberReader.findByUserId(DEMO_USER_ID)).willReturn(List.of(memberInRoom(50L)));
        given(roomReader.findCurrent(eq(List.of(50L)), any())).willReturn(Optional.of(anyRoom()));
        given(roomMemberReader.getByRoomId(100L)).willReturn(members);
    }

    @Test
    @DisplayName("반대 성별 유령과 상호 성사시켜 MatchResult 저장·MutualMatchEvent를 발행한다")
    void givenNotMatched_whenForceMatch_thenSavesAndPublishes() {
        stubCurrentRoomWithMembers(List.of(demoMember(),
                ghostMember(2L, 20L, "MALE"), ghostMember(3L, 30L, "FEMALE")));
        given(matchResultReader.isMatched(9L, 3L)).willReturn(false);
        given(matchResultWriter.save(any())).willAnswer(inv -> {
            MatchResult mr = inv.getArgument(0);
            return new MatchResult(500L, mr.memberAId(), mr.memberBId(), mr.matchedAt());
        });

        service.forceMatch(DEMO_USER_ID);

        // 반대 성별(FEMALE, memberId 3) 과 성사
        verify(matchResultWriter).save(argThat(
                mr -> (mr.memberAId() == 3L || mr.memberBId() == 3L) && (mr.memberAId() == 9L || mr.memberBId() == 9L)));
        var captor = ArgumentCaptor.forClass(MutualMatchEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userIdA()).isEqualTo(DEMO_USER_ID);
        assertThat(captor.getValue().userIdB()).isEqualTo(30L);
    }

    @Test
    @DisplayName("이미 성사됐으면 아무 것도 하지 않는다(멱등)")
    void givenAlreadyMatched_whenForceMatch_thenNoOp() {
        stubCurrentRoomWithMembers(List.of(demoMember(), ghostMember(3L, 30L, "FEMALE")));
        given(matchResultReader.isMatched(9L, 3L)).willReturn(true);

        service.forceMatch(DEMO_USER_ID);

        verify(matchResultWriter, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("현재 방이 없으면 no-op")
    void givenNoRoom_whenForceMatch_thenNoOp() {
        given(roomMemberReader.findByUserId(DEMO_USER_ID)).willReturn(List.of());

        service.forceMatch(DEMO_USER_ID);

        verify(matchResultWriter, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
