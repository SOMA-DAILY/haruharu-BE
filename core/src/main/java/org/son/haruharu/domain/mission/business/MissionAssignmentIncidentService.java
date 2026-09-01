package org.son.haruharu.domain.mission.business;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncident;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReader;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReason;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentWriter;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class MissionAssignmentIncidentService {

    private static final Logger log = LoggerFactory.getLogger(MissionAssignmentIncidentService.class);

    private final MissionAssignmentIncidentReader reader;
    private final MissionAssignmentIncidentWriter writer;

    public MissionAssignmentIncidentService(
            MissionAssignmentIncidentReader reader,
            MissionAssignmentIncidentWriter writer
    ) {
        this.reader = reader;
        this.writer = writer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            RoomMissionSlot slot,
            MissionAssignmentIncidentStatus status,
            MissionAssignmentIncidentReason reason,
            String safeErrorSummary,
            LocalDateTime now
    ) {
        persist(slot, status, reason, safeErrorSummary, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordInAssignmentTransaction(
            RoomMissionSlot slot,
            MissionAssignmentIncidentStatus status,
            MissionAssignmentIncidentReason reason,
            String safeErrorSummary,
            LocalDateTime now
    ) {
        persist(slot, status, reason, safeErrorSummary, now);
    }

    private void persist(
            RoomMissionSlot slot,
            MissionAssignmentIncidentStatus status,
            MissionAssignmentIncidentReason reason,
            String safeErrorSummary,
            LocalDateTime now
    ) {
        MissionAssignmentIncident incident = reader.findBySlotId(slot.id())
                .map(existing -> existing.record(status, reason, safeErrorSummary, now))
                .orElseGet(() -> MissionAssignmentIncident.first(
                        slot.id(), status, reason, safeErrorSummary, now));
        MissionAssignmentIncident saved = writer.save(incident);
        var event = status == MissionAssignmentIncidentStatus.FAILED
                ? log.atError() : log.atWarn();
        event.addKeyValue("event", "MISSION_ASSIGNMENT_INCIDENT")
                .addKeyValue("status", saved.status())
                .addKeyValue("reasonCode", saved.reason())
                .addKeyValue("roomId", slot.roomId())
                .addKeyValue("slotId", slot.id())
                .addKeyValue("dayNumber", slot.dayNumber())
                .addKeyValue("period", slot.period())
                .addKeyValue("attemptCount", saved.attemptCount())
                .addKeyValue("opensAt", slot.opensAt())
                .addKeyValue("closesAt", slot.closesAt())
                .log("미션 배정 incident");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolve(Long roomMissionSlotId, LocalDateTime now) {
        persistResolution(roomMissionSlotId, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void resolveInAssignmentTransaction(Long roomMissionSlotId, LocalDateTime now) {
        persistResolution(roomMissionSlotId, now);
    }

    private void persistResolution(Long roomMissionSlotId, LocalDateTime now) {
        reader.findBySlotId(roomMissionSlotId)
                .filter(incident -> incident.status() != MissionAssignmentIncidentStatus.DEGRADED)
                .filter(incident -> incident.status() != MissionAssignmentIncidentStatus.RESOLVED)
                .map(incident -> writer.save(incident.resolved(now)))
                .ifPresent(incident -> log.atInfo()
                        .addKeyValue("event", "MISSION_ASSIGNMENT_INCIDENT_RESOLVED")
                        .addKeyValue("slotId", roomMissionSlotId)
                        .addKeyValue("attemptCount", incident.attemptCount())
                        .log("미션 배정 incident 해결"));
    }
}
