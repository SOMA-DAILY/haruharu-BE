package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReason;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MissionAssignmentExecutorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 2, 11, 30);
    private static final RoomMissionSlot SLOT = new RoomMissionSlot(
            100L, 10L, 1, MissionPeriod.MORNING,
            NOW.plusMinutes(10), NOW.plusHours(5), NOW.minusDays(1));

    @Mock RoomReader roomReader;
    @Mock RoomMissionReader roomMissionReader;
    @Mock MissionAssignmentContextFactory contextFactory;
    @Mock MissionAssigner missionAssigner;
    @Mock RoomMissionWriter roomMissionWriter;
    @Mock MissionAssignmentIncidentService incidentService;
    @Mock MissionAssignmentContext context;
    @Mock MissionDecision decision;
    @Mock RoomMission roomMission;
    @Mock RoomMission savedMission;

    MissionAssignmentExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new MissionAssignmentExecutor(
                roomReader, roomMissionReader, contextFactory, missionAssigner,
                roomMissionWriter, incidentService);
    }

    @Test
    @DisplayName("이미 배정된 슬롯은 컨텍스트를 다시 만들지 않고 멱등 스킵한다")
    void givenAssignedSlot_whenAssign_thenSkipsBeforeLoadingRoom() {
        given(roomMissionReader.isAssignedToSlot(SLOT.id())).willReturn(true);

        MissionAssignmentExecution execution = executor.assign(SLOT, NOW);

        assertThat(execution.skipped()).isTrue();
        then(roomReader).shouldHaveNoInteractions();
        then(contextFactory).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("취소된 방은 배정 직전 재검증에서도 스킵한다")
    void givenCancelledRoom_whenAssign_thenSkips() {
        Room cancelled = scheduledRoom().cancelled();
        given(roomMissionReader.isAssignedToSlot(SLOT.id())).willReturn(false);
        given(roomReader.findById(SLOT.roomId())).willReturn(Optional.of(cancelled));

        MissionAssignmentExecution execution = executor.assign(SLOT, NOW);

        assertThat(execution.skipped()).isTrue();
        then(contextFactory).shouldHaveNoInteractions();
        then(roomMissionWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("정상 슬롯은 컨텍스트 생성, 정책 결정, 저장을 한 트랜잭션에서 수행한다")
    void givenDueSlot_whenAssign_thenPersistsDecision() {
        Room room = scheduledRoom();
        given(roomMissionReader.isAssignedToSlot(SLOT.id())).willReturn(false);
        given(roomReader.findById(SLOT.roomId())).willReturn(Optional.of(room));
        given(contextFactory.build(room, SLOT, NOW)).willReturn(context);
        given(missionAssigner.assignNext(context)).willReturn(decision);
        given(decision.roomMission()).willReturn(roomMission);
        given(roomMissionWriter.save(roomMission)).willReturn(savedMission);
        given(savedMission.id()).willReturn(500L);
        given(savedMission.relaxationLevel()).willReturn(MissionAssignmentRelaxationLevel.NONE);

        MissionAssignmentExecution execution = executor.assign(SLOT, NOW);

        assertThat(execution.roomMissionId()).isEqualTo(500L);
        assertThat(execution.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.NONE);
        assertThat(execution.skipped()).isFalse();
        then(roomMissionWriter).should().save(roomMission);
        then(incidentService).should().resolveInAssignmentTransaction(SLOT.id(), NOW);
    }

    @Test
    @DisplayName("fallback 배정과 DEGRADED incident는 같은 트랜잭션에서 저장한다")
    void givenFallbackDecision_whenAssign_thenRecordsIncidentInAssignmentTransaction() {
        Room room = scheduledRoom();
        given(roomMissionReader.isAssignedToSlot(SLOT.id())).willReturn(false);
        given(roomReader.findById(SLOT.roomId())).willReturn(Optional.of(room));
        given(contextFactory.build(room, SLOT, NOW)).willReturn(context);
        given(missionAssigner.assignNext(context)).willReturn(decision);
        given(decision.roomMission()).willReturn(roomMission);
        given(roomMissionWriter.save(roomMission)).willReturn(savedMission);
        given(savedMission.id()).willReturn(501L);
        given(savedMission.relaxationLevel()).willReturn(MissionAssignmentRelaxationLevel.SAFE_FALLBACK);

        MissionAssignmentExecution execution = executor.assign(SLOT, NOW);

        assertThat(execution.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.SAFE_FALLBACK);
        then(incidentService).should().recordInAssignmentTransaction(
                SLOT, MissionAssignmentIncidentStatus.DEGRADED,
                MissionAssignmentIncidentReason.SAFE_FALLBACK_USED,
                "SAFE_FALLBACK", NOW);
    }

    private Room scheduledRoom() {
        return new Room(
                10L, RoomStatus.PENDING,
                NOW.toLocalDate(), 3, null, null,
                NOW.minusHours(1), NOW.plusDays(2), NOW.plusDays(2).plusHours(2),
                NOW.plusDays(2).plusHours(3), NOW.plusDays(3));
    }
}
