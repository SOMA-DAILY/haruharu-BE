package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncident;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReader;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReason;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentWriter;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MissionAssignmentIncidentServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 2, 11, 30);
    private static final RoomMissionSlot SLOT = new RoomMissionSlot(
            100L, 10L, 1, MissionPeriod.MORNING,
            NOW.plusMinutes(10), NOW.plusHours(5), NOW.minusDays(1));

    @Mock MissionAssignmentIncidentReader reader;
    @Mock MissionAssignmentIncidentWriter writer;

    MissionAssignmentIncidentService service;

    @BeforeEach
    void setUp() {
        service = new MissionAssignmentIncidentService(reader, writer);
    }

    @Test
    @DisplayName("첫 장애는 슬롯 단위 FAILED incident와 안전한 요약을 저장한다")
    void givenNoIncident_whenRecordFailure_thenPersistsFirstIncident() {
        given(reader.findBySlotId(SLOT.id())).willReturn(Optional.empty());
        given(writer.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.record(
                SLOT, MissionAssignmentIncidentStatus.FAILED,
                MissionAssignmentIncidentReason.ASSIGNMENT_DEADLINE_MISSED,
                "NoMissionCandidateException", NOW);

        ArgumentCaptor<MissionAssignmentIncident> captor =
                ArgumentCaptor.forClass(MissionAssignmentIncident.class);
        then(writer).should().save(captor.capture());
        MissionAssignmentIncident saved = captor.getValue();
        assertThat(saved.roomMissionSlotId()).isEqualTo(SLOT.id());
        assertThat(saved.status()).isEqualTo(MissionAssignmentIncidentStatus.FAILED);
        assertThat(saved.attemptCount()).isOne();
        assertThat(saved.lastErrorSummary()).isEqualTo("NoMissionCandidateException");
    }

    @Test
    @DisplayName("기존 장애가 있으면 시도 횟수를 올리고 최신 상태로 저장한다")
    void givenExistingIncident_whenRecordAgain_thenUpdatesIt() {
        MissionAssignmentIncident existing = MissionAssignmentIncident.first(
                SLOT.id(), MissionAssignmentIncidentStatus.RETRYING,
                MissionAssignmentIncidentReason.SAFE_FALLBACK_MISSING,
                "NoMissionCandidateException", NOW.minusMinutes(1));
        given(reader.findBySlotId(SLOT.id())).willReturn(Optional.of(existing));
        given(writer.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.record(
                SLOT, MissionAssignmentIncidentStatus.RETRYING,
                MissionAssignmentIncidentReason.ASSIGNMENT_PERSISTENCE_FAILED,
                "DataAccessException", NOW);

        ArgumentCaptor<MissionAssignmentIncident> captor =
                ArgumentCaptor.forClass(MissionAssignmentIncident.class);
        then(writer).should().save(captor.capture());
        assertThat(captor.getValue().attemptCount()).isEqualTo(2);
        assertThat(captor.getValue().reason())
                .isEqualTo(MissionAssignmentIncidentReason.ASSIGNMENT_PERSISTENCE_FAILED);
    }

    @Test
    @DisplayName("재시도 장애는 정상 배정 뒤 RESOLVED로 전환한다")
    void givenRetryingIncident_whenResolve_thenPersistsResolvedStatus() {
        MissionAssignmentIncident retrying = MissionAssignmentIncident.first(
                SLOT.id(), MissionAssignmentIncidentStatus.RETRYING,
                MissionAssignmentIncidentReason.ASSIGNMENT_PERSISTENCE_FAILED,
                "DataAccessException", NOW.minusMinutes(1));
        given(reader.findBySlotId(SLOT.id())).willReturn(Optional.of(retrying));
        given(writer.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        service.resolve(SLOT.id(), NOW);

        ArgumentCaptor<MissionAssignmentIncident> captor =
                ArgumentCaptor.forClass(MissionAssignmentIncident.class);
        then(writer).should().save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(MissionAssignmentIncidentStatus.RESOLVED);
        assertThat(captor.getValue().resolvedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("fallback 사용을 뜻하는 DEGRADED incident는 정상 배정으로 자동 해소하지 않는다")
    void givenDegradedIncident_whenResolve_thenKeepsDegraded() {
        MissionAssignmentIncident degraded = MissionAssignmentIncident.first(
                SLOT.id(), MissionAssignmentIncidentStatus.DEGRADED,
                MissionAssignmentIncidentReason.SAFE_FALLBACK_USED,
                "SAFE_FALLBACK", NOW.minusMinutes(1));
        given(reader.findBySlotId(SLOT.id())).willReturn(Optional.of(degraded));

        service.resolve(SLOT.id(), NOW);

        then(writer).shouldHaveNoInteractions();
    }
}
