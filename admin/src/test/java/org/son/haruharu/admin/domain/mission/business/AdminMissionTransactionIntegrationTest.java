package org.son.haruharu.admin.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.admin.domain.mission.business.command.CreateMissionCommand;
import org.son.haruharu.admin.domain.mission.business.command.MissionEligibilityCommand;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionReader;
import org.son.haruharu.admin.domain.mission.implement.AdminMissionWriter;
import org.son.haruharu.admin.domain.mission.storage.AdminMissionJpaRepository;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.storage.MissionRevisionEligibilityJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionRevisionJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
class AdminMissionTransactionIntegrationTest {

    @Autowired AdminMissionService service;
    @Autowired AdminMissionReader reader;
    @Autowired AdminMissionJpaRepository missionJpaRepository;
    @Autowired MissionRevisionJpaRepository revisionJpaRepository;
    @Autowired MissionRevisionEligibilityJpaRepository eligibilityJpaRepository;
    @MockitoSpyBean AdminMissionWriter writer;

    @BeforeEach
    void setUp() {
        eligibilityJpaRepository.deleteAll();
        revisionJpaRepository.deleteAll();
        missionJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("eligibility 저장 직후 실패하면 새 revision이 롤백되고 기존 포인터가 유지된다")
    void givenFailureAfterEligibilitySave_whenPublishRevision_thenRollsBackAndKeepsPointer() {
        Long missionId = service.create(command("첫 문구")).id();
        Long originalRevisionId = reader.getById(missionId).activeRevisionId();
        long originalEligibilityCount = eligibilityJpaRepository.count();
        doAnswer(invocation -> {
            Object saved = invocation.callRealMethod();
            throw new IllegalStateException("forced failure after eligibility storage: " + saved);
        }).when(writer).saveEligibilities(any());

        assertThatThrownBy(() -> service.publishRevision(missionId, command("새 문구")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(reader.getById(missionId).activeRevisionId()).isEqualTo(originalRevisionId);
        assertThat(revisionJpaRepository.countByMissionId(missionId)).isEqualTo(1);
        assertThat(eligibilityJpaRepository.count()).isEqualTo(originalEligibilityCount);
    }

    @Test
    @DisplayName("revision 발행과 비활성화가 동시에 실행돼도 새 포인터와 비활성 상태를 모두 보존한다")
    void givenConcurrentPublishAndDeactivate_whenBothCommit_thenPreservesRevisionAndStatus() throws Exception {
        Long missionId = service.create(command("첫 문구")).id();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var publish = executor.submit(() -> {
                await(start);
                service.publishRevision(missionId, command("새 문구"));
            });
            var deactivate = executor.submit(() -> {
                await(start);
                service.changeStatus(missionId, false);
            });
            start.countDown();
            publish.get(5, TimeUnit.SECONDS);
            deactivate.get(5, TimeUnit.SECONDS);
        }

        var result = reader.getById(missionId);
        assertThat(result.active()).isFalse();
        assertThat(reader.getRevision(result.activeRevisionId()).revisionNumber()).isEqualTo(2);
        assertThat(revisionJpaRepository.countByMissionId(missionId)).isEqualTo(2);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private CreateMissionCommand command(String description) {
        return new CreateMissionCommand(
                description, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "TRANSACTION", new BigDecimal("0.8"), false,
                List.of(new MissionEligibilityCommand(1, MissionPeriod.MORNING)));
    }
}
