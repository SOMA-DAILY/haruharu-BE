package org.son.haruharu.admin.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.domain.mission.business.command.CreateMissionCommand;
import org.son.haruharu.admin.domain.mission.business.command.MissionEligibilityCommand;
import org.son.haruharu.admin.domain.mission.business.dto.AdminMissionResponse;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionReader;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionWriter;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.exception.InvalidMissionRevisionEligibilityException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminMissionServiceTest {

    @Mock AdminMissionReader reader;
    @Mock AdminMissionWriter writer;

    AdminMissionService service;

    @BeforeEach
    void setUp() {
        service = new AdminMissionService(reader, writer);
    }

    @Test
    @DisplayName("신규 미션은 논리 Mission, revision 1, eligibility를 저장하고 현재 포인터를 연결한다")
    void givenValidCommand_whenCreate_thenSavesAggregateAndActivatesFirstRevision() {
        Mission logicalMission = new Mission(1L, null, true);
        MissionRevision revision = revision(10L, 1, "첫 문구");
        given(writer.saveMission(Mission.create())).willReturn(logicalMission);
        given(writer.saveRevision(any())).willReturn(revision);
        given(writer.saveEligibilities(any())).willReturn(eligibilities(10L));
        given(writer.saveMission(logicalMission.activateRevision(10L)))
                .willReturn(logicalMission.activateRevision(10L));

        AdminMissionResponse result = service.create(command("첫 문구"));

        assertThat(result.activeRevisionId()).isEqualTo(10L);
        assertThat(result.activeRevision().revisionNumber()).isEqualTo(1);
        assertThat(result.activeRevision().eligibilities()).hasSize(2);
    }

    @Test
    @DisplayName("새 revision 발행은 행 잠금 후 다음 번호를 만들고 현재 포인터만 교체한다")
    void givenExistingRevision_whenPublishRevision_thenPreservesHistoryAndMovesPointer() {
        Mission mission = new Mission(1L, 10L, true);
        given(reader.getByIdForUpdate(1L)).willReturn(mission);
        given(reader.getLatestRevision(1L)).willReturn(revision(10L, 1, "옛 문구"));
        given(writer.saveRevision(any())).willReturn(revision(11L, 2, "새 문구"));
        given(writer.saveEligibilities(any())).willReturn(eligibilities(11L));
        given(writer.saveMission(mission.activateRevision(11L))).willReturn(mission.activateRevision(11L));

        AdminMissionResponse result = service.publishRevision(1L, command("새 문구"));

        ArgumentCaptor<MissionRevision> revisionCaptor = ArgumentCaptor.forClass(MissionRevision.class);
        verify(writer).saveRevision(revisionCaptor.capture());
        assertThat(revisionCaptor.getValue().revisionNumber()).isEqualTo(2);
        assertThat(result.activeRevisionId()).isEqualTo(11L);
        assertThat(result.activeRevision().description()).isEqualTo("새 문구");
    }

    @Test
    @DisplayName("eligibility 저장 실패 시 현재 포인터 저장까지 진행하지 않는다")
    void givenEligibilityStorageFailure_whenPublishRevision_thenDoesNotMovePointer() {
        Mission mission = new Mission(1L, 10L, true);
        given(reader.getByIdForUpdate(1L)).willReturn(mission);
        given(reader.getLatestRevision(1L)).willReturn(revision(10L, 1, "옛 문구"));
        given(writer.saveRevision(any())).willReturn(revision(11L, 2, "새 문구"));
        given(writer.saveEligibilities(any())).willThrow(new IllegalStateException("storage failure"));

        assertThatThrownBy(() -> service.publishRevision(1L, command("새 문구")))
                .isInstanceOf(IllegalStateException.class);

        verify(writer, never()).saveMission(any());
    }

    @Test
    @DisplayName("중복 Day/period eligibility는 쓰기 전에 거부한다")
    void givenDuplicateEligibility_whenCreate_thenRejectsBeforeWrite() {
        CreateMissionCommand command = command("문구", List.of(
                new MissionEligibilityCommand(1, MissionPeriod.MORNING),
                new MissionEligibilityCommand(1, MissionPeriod.MORNING)));

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(InvalidMissionRevisionEligibilityException.class);

        verify(writer, never()).saveMission(any());
        verify(writer, never()).saveRevision(any());
    }

    @Test
    @DisplayName("비활성화는 논리 Mission 상태만 바꾸고 현재 revision 포인터를 유지한다")
    void givenActiveMission_whenChangeStatus_thenDeactivatesLogicalMissionOnly() {
        Mission mission = new Mission(1L, 10L, true);
        given(reader.getByIdForUpdate(1L)).willReturn(mission);
        given(writer.saveMission(mission.changeActive(false))).willReturn(mission.changeActive(false));
        given(reader.getRevision(10L)).willReturn(revision(10L, 1, "문구"));
        given(reader.findEligibilities(10L)).willReturn(eligibilities(10L));

        AdminMissionResponse result = service.changeStatus(1L, false);

        assertThat(result.active()).isFalse();
        assertThat(result.activeRevisionId()).isEqualTo(10L);
        verify(writer, times(1)).saveMission(any());
    }

    @Test
    @DisplayName("미션 목록은 페이지 내 current revision과 eligibility를 각각 한 번에 조회한다")
    void givenMissionPage_whenGetMissions_thenLoadsRevisionGraphInBatches() {
        List<Mission> missions = List.of(new Mission(1L, 10L, true), new Mission(2L, 20L, false));
        given(reader.findAll(0, 20)).willReturn(missions);
        given(reader.countMissions()).willReturn(2L);
        given(reader.findRevisionsByIds(java.util.Set.of(10L, 20L))).willReturn(List.of(
                revision(10L, 1, "첫 미션"),
                new MissionRevision(20L, 2L, 1, "둘째 미션", MissionAttributeLevel.LOW,
                        MissionGoal.OBSERVATION, ActivationChannel.VIDEO, InteractionScope.SELF,
                        MissionAttributeLevel.LOW, MissionAttributeLevel.LOW, PreferenceLeakRisk.NONE,
                        MissionAttributeLevel.LOW, "SECOND", new BigDecimal("0.8"), false, null)));
        given(reader.findEligibilitiesByRevisionIds(java.util.Set.of(10L, 20L))).willReturn(List.of(
                MissionRevisionEligibility.of(10L, 1, MissionPeriod.MORNING),
                MissionRevisionEligibility.of(20L, 2, MissionPeriod.AFTERNOON)));

        var result = service.getMissions(0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.page().totalElements()).isEqualTo(2);
        verify(reader, never()).getRevision(any());
        verify(reader, never()).findEligibilities(any());
    }

    @Test
    @DisplayName("null eligibility 요소는 쓰기 전에 400 도메인 예외로 거부한다")
    void givenNullEligibilityElement_whenCreate_thenRejectsBeforeWrite() {
        CreateMissionCommand command = command("문구", java.util.Arrays.asList(
                new MissionEligibilityCommand(1, MissionPeriod.MORNING), null));

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(InvalidMissionRevisionEligibilityException.class);

        verify(writer, never()).saveMission(any());
    }

    private CreateMissionCommand command(String description) {
        return command(description, List.of(
                new MissionEligibilityCommand(1, MissionPeriod.MORNING),
                new MissionEligibilityCommand(1, MissionPeriod.AFTERNOON)));
    }

    private CreateMissionCommand command(String description, List<MissionEligibilityCommand> eligibilities) {
        return new CreateMissionCommand(
                description, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "DAILY_VIEW", new BigDecimal("0.8"), false, eligibilities);
    }

    private MissionRevision revision(Long id, int number, String description) {
        MissionRevision unsaved = MissionRevision.create(
                1L, number, description, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "DAILY_VIEW", new BigDecimal("0.8"), false);
        return new MissionRevision(id, unsaved.missionId(), unsaved.revisionNumber(), unsaved.description(),
                unsaved.disclosureDepth(), unsaved.missionGoal(), unsaved.activationChannel(),
                unsaved.interactionScope(), unsaved.participationBurden(), unsaved.privacyRisk(),
                unsaved.preferenceLeakRisk(), unsaved.captureDifficulty(), unsaved.semanticCluster(),
                unsaved.baseQualityScore(), unsaved.fallback(), null);
    }

    private List<MissionRevisionEligibility> eligibilities(Long revisionId) {
        return List.of(
                MissionRevisionEligibility.of(revisionId, 1, MissionPeriod.MORNING),
                MissionRevisionEligibility.of(revisionId, 1, MissionPeriod.AFTERNOON));
    }
}
