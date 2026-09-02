package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.MissionCatalogEntry;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomStateReader;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MissionAssignmentContextFactory {

    // 방 상태 스냅샷이 추가되어 구조가 바뀌었다. 과거 로그와 섞이지 않게 버전을 올린다.
    static final String CONTEXT_SCHEMA_VERSION = "context-v2";

    private final MissionReader missionReader;
    private final RoomMissionReader roomMissionReader;
    private final RoomMissionSlotReader roomMissionSlotReader;
    private final MissionAssignmentSeedGenerator seedGenerator;
    private final RoomStateReader roomStateReader;
    private final String applicationCommitSha;

    public MissionAssignmentContextFactory(
            MissionReader missionReader,
            RoomMissionReader roomMissionReader,
            RoomMissionSlotReader roomMissionSlotReader,
            MissionAssignmentSeedGenerator seedGenerator,
            RoomStateReader roomStateReader,
            @Value("${haruharu.application.commit-sha:unknown}") String applicationCommitSha
    ) {
        this.missionReader = missionReader;
        this.roomMissionReader = roomMissionReader;
        this.roomMissionSlotReader = roomMissionSlotReader;
        this.seedGenerator = seedGenerator;
        this.roomStateReader = roomStateReader;
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
        // 이미 쓴 revision 은 버리지 않고 따로 담는다. Rule V1 이 마지막 완화 단계에서만 합류시킨다.
        List<MissionRevision> previouslyUsedCandidates = catalog.stream()
                .filter(entry -> !entry.revision().fallback())
                .filter(entry -> entry.eligibleFor(slot.dayNumber(), slot.period()))
                .map(MissionCatalogEntry::revision)
                .filter(revision -> previouslyUsed.contains(revision.id()))
                .sorted(Comparator.comparing(MissionRevision::id))
                .toList();
        List<MissionRevision> fallback = catalog.stream()
                .filter(entry -> entry.revision().fallback())
                .filter(entry -> entry.eligibleFor(slot.dayNumber(), slot.period()))
                .map(MissionCatalogEntry::revision)
                .sorted(Comparator.comparing(MissionRevision::id))
                .toList();
        // 연속 중복 판정에는 속성이 필요하다. 비활성화된 revision 도 이력이므로 ID 로 다시 읽는다.
        List<MissionRevision> previousRevisions = orderedPreviousRevisions(previous);
        int sequence = sequenceOf(slot, roomMissionSlotReader.getByRoomId(room.id()));
        return new MissionAssignmentContext(
                room, slot, sequence, previousRevisionIds, previousRevisions,
                normal, previouslyUsedCandidates, fallback,
                roomStateReader.snapshot(room.id(), previous),
                CONTEXT_SCHEMA_VERSION, now, now, catalogVersion(catalog),
                applicationCommitSha, seedGenerator.nextSeed());
    }

    /** 배정 순서(sequence)대로 정렬한 이전 revision. */
    private List<MissionRevision> orderedPreviousRevisions(List<RoomMission> previous) {
        List<RoomMission> ordered = previous.stream()
                .sorted(Comparator.comparingInt(RoomMission::sequence))
                .toList();
        Map<Long, MissionRevision> byId = missionReader
                .getRevisionsByIds(ordered.stream().map(RoomMission::missionRevisionId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(MissionRevision::id, revision -> revision, (a, b) -> a));
        return ordered.stream()
                .map(roomMission -> byId.get(roomMission.missionRevisionId()))
                .filter(java.util.Objects::nonNull)
                .toList();
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
