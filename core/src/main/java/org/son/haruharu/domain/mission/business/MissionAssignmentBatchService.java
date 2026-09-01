package org.son.haruharu.domain.mission.business;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.mission.business.exception.NoMissionCandidateException;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReason;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotReader;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MissionAssignmentBatchService {

    private static final Logger log = LoggerFactory.getLogger(MissionAssignmentBatchService.class);

    private final RoomMissionSlotReader slotReader;
    private final MissionAssignmentExecutor executor;
    private final MissionAssignmentIncidentService incidentService;
    private final RoomMissionReader roomMissionReader;

    public MissionAssignmentBatchService(
            RoomMissionSlotReader slotReader,
            MissionAssignmentExecutor executor,
            MissionAssignmentIncidentService incidentService,
            RoomMissionReader roomMissionReader
    ) {
        this.slotReader = slotReader;
        this.executor = executor;
        this.incidentService = incidentService;
        this.roomMissionReader = roomMissionReader;
    }

    public MissionAssignmentBatchResult processDueSlots(
            LocalDateTime now, Duration assignmentLead, int batchSize) {
        Counters counters = new Counters();
        List<RoomMissionSlot> expired = slotReader.getExpiredUnassigned(now, batchSize);
        for (RoomMissionSlot slot : expired) {
            counters.examined++;
            counters.failed++;
            safeRecord(slot, MissionAssignmentIncidentStatus.FAILED,
                    MissionAssignmentIncidentReason.ASSIGNMENT_DEADLINE_MISSED,
                    "SLOT_EXPIRED_UNASSIGNED", now);
        }

        List<RoomMissionSlot> due = slotReader.getDueUnassigned(
                now, now.plus(assignmentLead), batchSize);
        for (RoomMissionSlot slot : due) {
            counters.examined++;
            processOne(slot, now, counters);
        }
        return counters.result();
    }

    private void processOne(RoomMissionSlot slot, LocalDateTime now, Counters counters) {
        try {
            MissionAssignmentExecution execution = executor.assign(slot, now);
            if (execution.skipped()) {
                counters.skipped++;
                return;
            }
            counters.assigned++;
            if (execution.relaxationLevel() == MissionAssignmentRelaxationLevel.SAFE_FALLBACK) {
                counters.degraded++;
            }
        } catch (NoMissionCandidateException noCandidate) {
            if (now.isBefore(slot.opensAt())) {
                counters.retrying++;
                safeRecord(slot, MissionAssignmentIncidentStatus.RETRYING,
                        MissionAssignmentIncidentReason.SAFE_FALLBACK_MISSING,
                        noCandidate.getClass().getSimpleName(), now);
            } else {
                counters.failed++;
                safeRecord(slot, MissionAssignmentIncidentStatus.FAILED,
                        MissionAssignmentIncidentReason.ASSIGNMENT_DEADLINE_MISSED,
                        noCandidate.getClass().getSimpleName(), now);
            }
        } catch (DataIntegrityViolationException raceOrConstraint) {
            try {
                if (roomMissionReader.isAssignedToSlot(slot.id())) {
                    counters.skipped++;
                    log.atInfo()
                            .addKeyValue("event", "MISSION_ASSIGNMENT_IDEMPOTENT_SKIP")
                            .addKeyValue("slotId", slot.id())
                            .log("동시 미션 배정 레이스 멱등 스킵");
                    return;
                }
            } catch (DataAccessException recheckFailure) {
                recordFailure(slot, now, counters,
                        MissionAssignmentIncidentReason.ASSIGNMENT_PERSISTENCE_FAILED,
                        recheckFailure.getClass().getSimpleName());
                return;
            }
            recordFailure(slot, now, counters,
                    MissionAssignmentIncidentReason.ASSIGNMENT_PERSISTENCE_FAILED,
                    raceOrConstraint.getClass().getSimpleName());
        } catch (DataAccessException persistenceFailure) {
            recordFailure(slot, now, counters,
                    MissionAssignmentIncidentReason.ASSIGNMENT_PERSISTENCE_FAILED,
                    persistenceFailure.getClass().getSimpleName());
        } catch (RuntimeException unexpected) {
            recordFailure(slot, now, counters,
                    MissionAssignmentIncidentReason.UNEXPECTED_ASSIGNMENT_ERROR,
                    unexpected.getClass().getSimpleName());
        }
    }

    private void recordFailure(
            RoomMissionSlot slot,
            LocalDateTime now,
            Counters counters,
            MissionAssignmentIncidentReason reason,
            String safeSummary
    ) {
        MissionAssignmentIncidentStatus status = now.isBefore(slot.opensAt())
                ? MissionAssignmentIncidentStatus.RETRYING
                : MissionAssignmentIncidentStatus.FAILED;
        if (status == MissionAssignmentIncidentStatus.RETRYING) {
            counters.retrying++;
        } else {
            counters.failed++;
        }
        safeRecord(slot, status, reason, safeSummary, now);
    }

    private void safeRecord(
            RoomMissionSlot slot,
            MissionAssignmentIncidentStatus status,
            MissionAssignmentIncidentReason reason,
            String safeSummary,
            LocalDateTime now
    ) {
        try {
            incidentService.record(slot, status, reason, safeSummary, now);
        } catch (RuntimeException incidentFailure) {
            log.atError()
                    .addKeyValue("event", "MISSION_ASSIGNMENT_INCIDENT_PERSIST_FAILED")
                    .addKeyValue("slotId", slot.id())
                    .addKeyValue("reasonCode", reason)
                    .addKeyValue("errorType", incidentFailure.getClass().getSimpleName())
                    .log("미션 배정 incident 저장 실패");
        }
    }

    private static final class Counters {
        private int examined;
        private int assigned;
        private int degraded;
        private int retrying;
        private int failed;
        private int skipped;

        private MissionAssignmentBatchResult result() {
            return new MissionAssignmentBatchResult(
                    examined, assigned, degraded, retrying, failed, skipped);
        }
    }
}
