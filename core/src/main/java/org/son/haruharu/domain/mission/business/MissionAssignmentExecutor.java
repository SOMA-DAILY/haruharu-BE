package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReason;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class MissionAssignmentExecutor {

    private final RoomReader roomReader;
    private final RoomMissionReader roomMissionReader;
    private final MissionAssignmentContextFactory contextFactory;
    private final MissionAssigner missionAssigner;
    private final RoomMissionWriter roomMissionWriter;
    private final MissionAssignmentIncidentService incidentService;

    public MissionAssignmentExecutor(
            RoomReader roomReader,
            RoomMissionReader roomMissionReader,
            MissionAssignmentContextFactory contextFactory,
            MissionAssigner missionAssigner,
            RoomMissionWriter roomMissionWriter,
            MissionAssignmentIncidentService incidentService
    ) {
        this.roomReader = roomReader;
        this.roomMissionReader = roomMissionReader;
        this.contextFactory = contextFactory;
        this.missionAssigner = missionAssigner;
        this.roomMissionWriter = roomMissionWriter;
        this.incidentService = incidentService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MissionAssignmentExecution assign(RoomMissionSlot slot, LocalDateTime now) {
        if (roomMissionReader.isAssignedToSlot(slot.id())) {
            return MissionAssignmentExecution.skippedResult();
        }
        Room room = roomReader.findById(slot.roomId())
                .orElseThrow(() -> new IllegalStateException("Room not found for mission slot: roomId=" + slot.roomId()));
        RoomStatus status = room.statusAt(now);
        if (status == RoomStatus.CANCELLED || status == RoomStatus.ENDED) {
            return MissionAssignmentExecution.skippedResult();
        }

        MissionDecision decision = missionAssigner.assignNext(contextFactory.build(room, slot, now));
        RoomMission saved = roomMissionWriter.save(decision.roomMission());
        if (saved.relaxationLevel() == MissionAssignmentRelaxationLevel.SAFE_FALLBACK) {
            incidentService.recordInAssignmentTransaction(
                    slot, MissionAssignmentIncidentStatus.DEGRADED,
                    MissionAssignmentIncidentReason.SAFE_FALLBACK_USED,
                    "SAFE_FALLBACK", now);
        } else {
            incidentService.resolveInAssignmentTransaction(slot.id(), now);
        }
        return MissionAssignmentExecution.assigned(saved.id(), saved.relaxationLevel());
    }
}
