package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.business.exception.NoMissionCandidateException;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReason;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotReader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MissionAssignmentBatchServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 2, 11, 30);

    @Mock RoomMissionSlotReader slotReader;
    @Mock MissionAssignmentExecutor executor;
    @Mock MissionAssignmentIncidentService incidentService;
    @Mock RoomMissionReader roomMissionReader;

    MissionAssignmentBatchService service;

    @BeforeEach
    void setUp() {
        service = new MissionAssignmentBatchService(slotReader, executor, incidentService, roomMissionReader);
    }

    @Test
    @DisplayName("fallback 배정은 성공 건으로 저장하되 DEGRADED incident를 남긴다")
    void givenFallbackDecision_whenProcessDueSlots_thenRecordsDegradedIncident() {
        RoomMissionSlot slot = slot(100L, NOW.plusMinutes(20), NOW.plusHours(5));
        MissionAssignmentExecution execution = MissionAssignmentExecution.assigned(
                500L, MissionAssignmentRelaxationLevel.SAFE_FALLBACK);
        given(slotReader.getDueUnassigned(NOW, NOW.plusHours(1), 100)).willReturn(List.of(slot));
        given(slotReader.getExpiredUnassigned(NOW, 100)).willReturn(List.of());
        given(executor.assign(slot, NOW)).willReturn(execution);

        MissionAssignmentBatchResult result = service.processDueSlots(NOW, Duration.ofHours(1), 100);

        assertThat(result.assigned()).isEqualTo(1);
        assertThat(result.degraded()).isEqualTo(1);
        then(incidentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("오픈 전 후보 고갈은 RETRYING으로 기록하고 다른 슬롯 처리를 계속한다")
    void givenNoCandidateBeforeOpen_whenProcessDueSlots_thenRecordsRetryingAndContinues() {
        RoomMissionSlot failed = slot(100L, NOW.plusMinutes(10), NOW.plusHours(5));
        RoomMissionSlot succeeded = slot(101L, NOW.plusMinutes(20), NOW.plusHours(5));
        given(slotReader.getDueUnassigned(NOW, NOW.plusHours(1), 100))
                .willReturn(List.of(failed, succeeded));
        given(slotReader.getExpiredUnassigned(NOW, 100)).willReturn(List.of());
        given(executor.assign(failed, NOW)).willThrow(new NoMissionCandidateException());
        given(executor.assign(succeeded, NOW)).willReturn(
                MissionAssignmentExecution.assigned(501L, MissionAssignmentRelaxationLevel.NONE));

        MissionAssignmentBatchResult result = service.processDueSlots(NOW, Duration.ofHours(1), 100);

        assertThat(result.assigned()).isEqualTo(1);
        assertThat(result.retrying()).isEqualTo(1);
        then(incidentService).should().record(
                failed, MissionAssignmentIncidentStatus.RETRYING,
                MissionAssignmentIncidentReason.SAFE_FALLBACK_MISSING,
                "NoMissionCandidateException", NOW);
    }

    @Test
    @DisplayName("오픈 시각을 넘긴 미배정 슬롯은 FAILED로 승격한다")
    void givenNoCandidateAfterOpen_whenProcessDueSlots_thenRecordsFailed() {
        RoomMissionSlot slot = slot(100L, NOW.minusMinutes(1), NOW.plusHours(5));
        given(slotReader.getDueUnassigned(NOW, NOW.plusHours(1), 100)).willReturn(List.of(slot));
        given(slotReader.getExpiredUnassigned(NOW, 100)).willReturn(List.of());
        given(executor.assign(slot, NOW)).willThrow(new NoMissionCandidateException());

        MissionAssignmentBatchResult result = service.processDueSlots(NOW, Duration.ofHours(1), 100);

        assertThat(result.failed()).isEqualTo(1);
        then(incidentService).should().record(
                slot, MissionAssignmentIncidentStatus.FAILED,
                MissionAssignmentIncidentReason.ASSIGNMENT_DEADLINE_MISSED,
                "NoMissionCandidateException", NOW);
    }

    @Test
    @DisplayName("동시 저장 UNIQUE 충돌 뒤 슬롯이 배정돼 있으면 정상 멱등 스킵이다")
    void givenConcurrentInsertWonElsewhere_whenProcessDueSlots_thenSkipsWithoutIncident() {
        RoomMissionSlot slot = slot(100L, NOW.plusMinutes(10), NOW.plusHours(5));
        given(slotReader.getDueUnassigned(NOW, NOW.plusHours(1), 100)).willReturn(List.of(slot));
        given(slotReader.getExpiredUnassigned(NOW, 100)).willReturn(List.of());
        given(executor.assign(slot, NOW)).willThrow(new DataIntegrityViolationException("unique"));
        given(roomMissionReader.isAssignedToSlot(slot.id())).willReturn(true);

        MissionAssignmentBatchResult result = service.processDueSlots(NOW, Duration.ofHours(1), 100);

        assertThat(result.skipped()).isEqualTo(1);
        then(incidentService).should(never()).record(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("UNIQUE 충돌 뒤에도 슬롯이 비어 있으면 저장 장애로 기록한다")
    void givenConstraintFailureWithoutConcurrentAssignment_whenProcessDueSlots_thenRecordsPersistenceIncident() {
        RoomMissionSlot slot = slot(100L, NOW.plusMinutes(10), NOW.plusHours(5));
        given(slotReader.getDueUnassigned(NOW, NOW.plusHours(1), 100)).willReturn(List.of(slot));
        given(slotReader.getExpiredUnassigned(NOW, 100)).willReturn(List.of());
        given(executor.assign(slot, NOW)).willThrow(new DataIntegrityViolationException("constraint"));
        given(roomMissionReader.isAssignedToSlot(slot.id())).willReturn(false);

        MissionAssignmentBatchResult result = service.processDueSlots(NOW, Duration.ofHours(1), 100);

        assertThat(result.retrying()).isEqualTo(1);
        then(incidentService).should().record(
                slot, MissionAssignmentIncidentStatus.RETRYING,
                MissionAssignmentIncidentReason.ASSIGNMENT_PERSISTENCE_FAILED,
                "DataIntegrityViolationException", NOW);
    }

    @Test
    @DisplayName("UNIQUE 충돌 뒤 배정 여부 재확인도 실패해도 다른 슬롯 처리를 중단하지 않는다")
    void givenConstraintFailureAndRecheckFailure_whenProcessDueSlots_thenIsolatesFailure() {
        RoomMissionSlot failed = slot(100L, NOW.plusMinutes(10), NOW.plusHours(5));
        RoomMissionSlot succeeded = slot(101L, NOW.plusMinutes(20), NOW.plusHours(5));
        given(slotReader.getDueUnassigned(NOW, NOW.plusHours(1), 100))
                .willReturn(List.of(failed, succeeded));
        given(slotReader.getExpiredUnassigned(NOW, 100)).willReturn(List.of());
        given(executor.assign(failed, NOW)).willThrow(new DataIntegrityViolationException("constraint"));
        given(roomMissionReader.isAssignedToSlot(failed.id()))
                .willThrow(new DataAccessResourceFailureException("recheck"));
        given(executor.assign(succeeded, NOW)).willReturn(
                MissionAssignmentExecution.assigned(501L, MissionAssignmentRelaxationLevel.NONE));

        MissionAssignmentBatchResult result = service.processDueSlots(NOW, Duration.ofHours(1), 100);

        assertThat(result.retrying()).isEqualTo(1);
        assertThat(result.assigned()).isEqualTo(1);
        then(incidentService).should().record(
                failed, MissionAssignmentIncidentStatus.RETRYING,
                MissionAssignmentIncidentReason.ASSIGNMENT_PERSISTENCE_FAILED,
                "DataAccessResourceFailureException", NOW);
    }

    @Test
    @DisplayName("배치 중단 중 마감된 미배정 슬롯도 최종 FAILED incident로 남긴다")
    void givenExpiredUnassignedSlot_whenProcessDueSlots_thenRecordsTerminalFailure() {
        RoomMissionSlot expired = slot(100L, NOW.minusHours(6), NOW.minusMinutes(1));
        given(slotReader.getExpiredUnassigned(NOW, 100)).willReturn(List.of(expired));
        given(slotReader.getDueUnassigned(NOW, NOW.plusHours(1), 100)).willReturn(List.of());

        MissionAssignmentBatchResult result = service.processDueSlots(NOW, Duration.ofHours(1), 100);

        assertThat(result.failed()).isEqualTo(1);
        then(incidentService).should().record(
                expired, MissionAssignmentIncidentStatus.FAILED,
                MissionAssignmentIncidentReason.ASSIGNMENT_DEADLINE_MISSED,
                "SLOT_EXPIRED_UNASSIGNED", NOW);
    }

    private RoomMissionSlot slot(Long id, LocalDateTime opensAt, LocalDateTime closesAt) {
        return new RoomMissionSlot(id, 10L, 1, MissionPeriod.MORNING, opensAt, closesAt, NOW.minusDays(1));
    }
}
