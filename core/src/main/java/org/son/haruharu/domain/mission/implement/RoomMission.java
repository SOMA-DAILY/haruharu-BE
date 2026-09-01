package org.son.haruharu.domain.mission.implement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 방별 미션 인스턴스. 배정 시점의 불변 revision을 참조한다.
 * 방 공용(멤버별 아님)이며 revision은 배정 뒤 변경되지 않는다.
 * opensAt은 절대 시각이라 벽시계 고정이 아니라 방 시작(starts_at) 상대로 배정된다({@link MissionAssigner}).
 * closesAt이 null이면 무기한 열림.
 */
public record RoomMission(
        Long id,
        Long roomId,
        Long roomMissionSlotId,
        Long missionRevisionId,
        LocalDateTime opensAt,
        LocalDateTime closesAt,
        int sequence,
        String policyVersion,
        String catalogVersion,
        String contextSchemaVersion,
        LocalDateTime contextAsOf,
        MissionAssignmentContextSnapshot contextSnapshot,
        String candidateSchemaVersion,
        MissionCandidateSnapshot candidateSnapshot,
        BigDecimal selectedProbability,
        UUID explorationId,
        long rngSeed,
        String rngAlgorithm,
        MissionAssignmentRelaxationLevel relaxationLevel,
        String applicationCommitSha,
        LocalDateTime assignedAt,
        long assignmentDelaySeconds,
        LocalDateTime createdAt
) {
    /** 조회 테스트와 기존 카드 응답 픽스처용 생성자. 신규 저장은 {@link #assigned}를 사용한다. */
    public RoomMission(Long id, Long roomId, Long missionRevisionId, LocalDateTime opensAt,
                       LocalDateTime closesAt, int sequence, LocalDateTime createdAt) {
        this(id, roomId, null, missionRevisionId, opensAt, closesAt, sequence,
                null, null, null, null, null, null, null, null, null,
                0L, null, null, null, null, 0L, createdAt);
    }

    public static RoomMission assigned(
            Long roomId, Long roomMissionSlotId, Long missionRevisionId,
            LocalDateTime opensAt, LocalDateTime closesAt, int sequence,
            String policyVersion, String catalogVersion, String contextSchemaVersion,
            LocalDateTime contextAsOf, MissionAssignmentContextSnapshot contextSnapshot,
            String candidateSchemaVersion, MissionCandidateSnapshot candidateSnapshot,
            BigDecimal selectedProbability, UUID explorationId, long rngSeed,
            String rngAlgorithm, MissionAssignmentRelaxationLevel relaxationLevel,
            String applicationCommitSha, LocalDateTime assignedAt, long assignmentDelaySeconds
    ) {
        return new RoomMission(null, roomId, roomMissionSlotId, missionRevisionId, opensAt, closesAt, sequence,
                policyVersion, catalogVersion, contextSchemaVersion, contextAsOf, contextSnapshot,
                candidateSchemaVersion, candidateSnapshot, selectedProbability, explorationId, rngSeed,
                rngAlgorithm, relaxationLevel, applicationCommitSha, assignedAt, assignmentDelaySeconds, null);
    }

    /** now가 노출 구간 {@code [opensAt, closesAt)} 안인지. closesAt이 null이면 opensAt 이후 무기한. */
    public boolean isOpenAt(LocalDateTime now) {
        if (now.isBefore(opensAt)) {
            return false;
        }
        return closesAt == null || now.isBefore(closesAt);
    }
}
