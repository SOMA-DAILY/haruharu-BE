package org.son.haruharu.domain.mission.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MissionAssignmentIncidentTest {

    @Test
    @DisplayName("반복 실패는 최초 실패 시각을 보존하고 시도 횟수와 최근 상태를 갱신한다")
    void givenRetryingIncident_whenRecordFailureAgain_thenKeepsFirstFailureAndIncrementsAttempt() {
        LocalDateTime firstAt = LocalDateTime.of(2026, 9, 2, 11, 0);
        LocalDateTime secondAt = firstAt.plusMinutes(10);
        MissionAssignmentIncident first = MissionAssignmentIncident.first(
                100L, MissionAssignmentIncidentStatus.RETRYING,
                MissionAssignmentIncidentReason.SAFE_FALLBACK_MISSING,
                "NoMissionCandidateException", firstAt);

        MissionAssignmentIncident failed = first.record(
                MissionAssignmentIncidentStatus.FAILED,
                MissionAssignmentIncidentReason.ASSIGNMENT_DEADLINE_MISSED,
                "NoMissionCandidateException", secondAt);

        assertThat(failed.attemptCount()).isEqualTo(2);
        assertThat(failed.firstFailedAt()).isEqualTo(firstAt);
        assertThat(failed.lastFailedAt()).isEqualTo(secondAt);
        assertThat(failed.status()).isEqualTo(MissionAssignmentIncidentStatus.FAILED);
    }

    @Test
    @DisplayName("후속 정상 배정은 기존 incident를 RESOLVED로 전환한다")
    void givenFailedIncident_whenResolve_thenMarksResolvedAt() {
        LocalDateTime failedAt = LocalDateTime.of(2026, 9, 2, 11, 0);
        LocalDateTime resolvedAt = failedAt.plusMinutes(5);
        MissionAssignmentIncident incident = MissionAssignmentIncident.first(
                100L, MissionAssignmentIncidentStatus.RETRYING,
                MissionAssignmentIncidentReason.ASSIGNMENT_PERSISTENCE_FAILED,
                "DataAccessException", failedAt);

        MissionAssignmentIncident resolved = incident.resolved(resolvedAt);

        assertThat(resolved.status()).isEqualTo(MissionAssignmentIncidentStatus.RESOLVED);
        assertThat(resolved.resolvedAt()).isEqualTo(resolvedAt);
        assertThat(resolved.attemptCount()).isOne();
    }
}
