package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.MissionCatalogEntry;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotReader;
import org.son.haruharu.domain.room.implement.Room;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MissionAssignmentContextFactory {

    static final String CONTEXT_SCHEMA_VERSION = "context-v1";

    private final MissionReader missionReader;
    private final RoomMissionReader roomMissionReader;
    private final RoomMissionSlotReader roomMissionSlotReader;
    private final MissionAssignmentSeedGenerator seedGenerator;
    private final String applicationCommitSha;

    public MissionAssignmentContextFactory(
            MissionReader missionReader,
            RoomMissionReader roomMissionReader,
            RoomMissionSlotReader roomMissionSlotReader,
            MissionAssignmentSeedGenerator seedGenerator,
            @Value("${haruharu.application.commit-sha:unknown}") String applicationCommitSha
    ) {
        this.missionReader = missionReader;
        this.roomMissionReader = roomMissionReader;
        this.roomMissionSlotReader = roomMissionSlotReader;
        this.seedGenerator = seedGenerator;
        this.applicationCommitSha = applicationCommitSha;
    }

    public MissionAssignmentContext build(Room room, RoomMissionSlot slot, LocalDateTime now) {
        List<RoomMission> previous = roomMissionReader.getByRoomId(room.id());
        List<Long> previousRevisionIds = previous.stream()
                .map(RoomMission::missionRevisionId).sorted().toList();
        Set<Long> previouslyUsed = Set.copyOf(previousRevisionIds);
        List<MissionCatalogEntry> catalog = missionReader.getActiveCatalog();
        List<MissionRevision> normal = catalog.stream()
                .filter(entry -> !entry.revision().fallback())
                .filter(entry -> entry.eligibleFor(slot.dayNumber(), slot.period()))
                .map(MissionCatalogEntry::revision)
                .filter(revision -> !previouslyUsed.contains(revision.id()))
                .sorted(Comparator.comparing(MissionRevision::id))
                .toList();
        List<MissionRevision> fallback = catalog.stream()
                .filter(entry -> entry.revision().fallback())
                .filter(entry -> entry.eligibleFor(slot.dayNumber(), slot.period()))
                .map(MissionCatalogEntry::revision)
                .sorted(Comparator.comparing(MissionRevision::id))
                .toList();
        int sequence = sequenceOf(slot, roomMissionSlotReader.getByRoomId(room.id()));
        return new MissionAssignmentContext(
                room, slot, sequence, previousRevisionIds, normal, fallback,
                CONTEXT_SCHEMA_VERSION, now, now, catalogVersion(catalog),
                applicationCommitSha, seedGenerator.nextSeed());
    }

    private int sequenceOf(RoomMissionSlot target, List<RoomMissionSlot> roomSlots) {
        List<RoomMissionSlot> ordered = roomSlots.stream()
                .sorted(Comparator.comparing(RoomMissionSlot::opensAt)
                        .thenComparing(RoomMissionSlot::period)
                        .thenComparing(RoomMissionSlot::id))
                .toList();
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).id().equals(target.id())) {
                return index;
            }
        }
        throw new IllegalStateException("Mission slot is not part of room schedule: slotId=" + target.id());
    }

    private String catalogVersion(List<MissionCatalogEntry> catalog) {
        String source = catalog.stream()
                .map(entry -> entry.revision().missionId() + ":" + entry.revision().id())
                .sorted()
                .collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
