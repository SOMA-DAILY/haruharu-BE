package org.son.haruharu.admin.domain.mission.business;

import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.mission.business.command.CreateMissionCommand;
import org.son.haruharu.admin.domain.mission.business.command.MissionEligibilityCommand;
import org.son.haruharu.admin.domain.mission.business.dto.AdminMissionResponse;
import org.son.haruharu.admin.domain.mission.business.dto.AdminMissionRevisionResponse;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionReader;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionWriter;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;
import org.son.haruharu.domain.mission.implement.exception.InvalidMissionRevisionEligibilityException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 어드민 미션 카탈로그 관리(생성·목록·revision 발행·상태 변경).
 * 여기서 관리하는 활성 미션이 방 배정(FixedTwicePerDayAssigner)의 후보 풀이 된다.
 */
@Service
@Transactional(readOnly = true)
public class AdminMissionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminMissionReader adminMissionReader;
    private final AdminMissionWriter adminMissionWriter;

    public AdminMissionService(AdminMissionReader adminMissionReader, AdminMissionWriter adminMissionWriter) {
        this.adminMissionReader = adminMissionReader;
        this.adminMissionWriter = adminMissionWriter;
    }

    public PageResponse<AdminMissionResponse> getMissions(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        List<Mission> missions = adminMissionReader.findAll(safePage * safeSize, safeSize);
        Map<Long, MissionRevision> revisionsById = revisionsById(missions.stream()
                .map(Mission::activeRevisionId).collect(Collectors.toSet()));
        Map<Long, List<MissionRevisionEligibility>> eligibilitiesByRevisionId = eligibilitiesByRevisionId(
                revisionsById.keySet());
        List<AdminMissionResponse> content = missions.stream()
                .map(mission -> AdminMissionResponse.of(mission, AdminMissionRevisionResponse.of(
                        revisionsById.get(mission.activeRevisionId()),
                        eligibilitiesByRevisionId.getOrDefault(mission.activeRevisionId(), List.of()))))
                .toList();
        return PageResponse.of(content, safePage, safeSize, adminMissionReader.countMissions());
    }

    @Transactional
    public AdminMissionResponse create(CreateMissionCommand command) {
        validateCommand(command);
        Mission mission = adminMissionWriter.saveMission(Mission.create());
        MissionRevision revision = adminMissionWriter.saveRevision(toRevision(mission.id(), 1, command));
        List<MissionRevisionEligibility> eligibilities = adminMissionWriter.saveEligibilities(
                toEligibilities(revision.id(), command.eligibilities()));
        Mission activated = adminMissionWriter.saveMission(mission.activateRevision(revision.id()));
        return AdminMissionResponse.of(activated, AdminMissionRevisionResponse.of(revision, eligibilities));
    }

    @Transactional
    public AdminMissionResponse publishRevision(Long missionId, CreateMissionCommand command) {
        validateCommand(command);
        Mission mission = adminMissionReader.getByIdForUpdate(missionId);
        int nextNumber = adminMissionReader.getLatestRevision(missionId).revisionNumber() + 1;
        MissionRevision revision = adminMissionWriter.saveRevision(toRevision(missionId, nextNumber, command));
        List<MissionRevisionEligibility> eligibilities = adminMissionWriter.saveEligibilities(
                toEligibilities(revision.id(), command.eligibilities()));
        Mission activated = adminMissionWriter.saveMission(mission.activateRevision(revision.id()));
        return AdminMissionResponse.of(activated, AdminMissionRevisionResponse.of(revision, eligibilities));
    }

    public PageResponse<AdminMissionRevisionResponse> getRevisions(
            Long missionId,
            int page,
            int size
    ) {
        adminMissionReader.getById(missionId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        List<MissionRevision> revisions = adminMissionReader.findRevisions(
                missionId, safePage * safeSize, safeSize);
        Map<Long, List<MissionRevisionEligibility>> eligibilitiesByRevisionId = eligibilitiesByRevisionId(
                revisions.stream().map(MissionRevision::id).collect(Collectors.toSet()));
        List<AdminMissionRevisionResponse> content = revisions.stream()
                .map(revision -> AdminMissionRevisionResponse.of(revision,
                        eligibilitiesByRevisionId.getOrDefault(revision.id(), List.of())))
                .toList();
        return PageResponse.of(content, safePage, safeSize, adminMissionReader.countRevisions(missionId));
    }

    @Transactional
    public AdminMissionResponse changeStatus(Long missionId, boolean active) {
        Mission mission = adminMissionReader.getByIdForUpdate(missionId);
        Mission updated = adminMissionWriter.saveMission(mission.changeActive(active));
        return toResponse(updated);
    }

    private AdminMissionResponse toResponse(Mission mission) {
        MissionRevision revision = adminMissionReader.getRevision(mission.activeRevisionId());
        return AdminMissionResponse.of(mission, AdminMissionRevisionResponse.of(
                revision, adminMissionReader.findEligibilities(revision.id())));
    }

    private MissionRevision toRevision(Long missionId, int revisionNumber, CreateMissionCommand command) {
        return MissionRevision.create(
                missionId, revisionNumber, command.description(), command.disclosureDepth(), command.missionGoal(),
                command.activationChannel(), command.interactionScope(), command.participationBurden(),
                command.privacyRisk(), command.preferenceLeakRisk(), command.captureDifficulty(),
                command.semanticCluster(), command.baseQualityScore(), command.fallback());
    }

    private List<MissionRevisionEligibility> toEligibilities(
            Long revisionId,
            List<MissionEligibilityCommand> commands
    ) {
        return commands.stream()
                .map(command -> MissionRevisionEligibility.of(revisionId, command.dayNumber(), command.period()))
                .toList();
    }

    private void validateDistinctEligibilities(List<MissionEligibilityCommand> eligibilities) {
        if (eligibilities == null || eligibilities.isEmpty() || eligibilities.stream().anyMatch(Objects::isNull)) {
            throw new InvalidMissionRevisionEligibilityException();
        }
        Set<MissionEligibilityCommand> distinct = new HashSet<>(eligibilities);
        if (distinct.size() != eligibilities.size()) {
            throw new InvalidMissionRevisionEligibilityException();
        }
    }

    /** 저장 전에 도메인 불변식을 전부 확인해 영속성 컨텍스트에도 부분 aggregate를 남기지 않는다. */
    private void validateCommand(CreateMissionCommand command) {
        validateDistinctEligibilities(command.eligibilities());
        toRevision(1L, 1, command);
        toEligibilities(1L, command.eligibilities());
    }

    private Map<Long, MissionRevision> revisionsById(Set<Long> revisionIds) {
        if (revisionIds.isEmpty()) {
            return Map.of();
        }
        return adminMissionReader.findRevisionsByIds(revisionIds).stream()
                .collect(Collectors.toMap(MissionRevision::id, Function.identity()));
    }

    private Map<Long, List<MissionRevisionEligibility>> eligibilitiesByRevisionId(Set<Long> revisionIds) {
        if (revisionIds.isEmpty()) {
            return Map.of();
        }
        return adminMissionReader.findEligibilitiesByRevisionIds(revisionIds).stream()
                .collect(Collectors.groupingBy(MissionRevisionEligibility::missionRevisionId));
    }
}
