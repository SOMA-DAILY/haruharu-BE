package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.MissionCatalogEntry;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MissionAssignmentContextFactoryTest {

    @Mock MissionReader missionReader;
    @Mock RoomMissionReader roomMissionReader;
    @Mock RoomMissionSlotReader slotReader;
    @Mock MissionAssignmentSeedGenerator seedGenerator;

    MissionAssignmentContextFactory factory;

    @BeforeEach
    void setUp() {
        factory = new MissionAssignmentContextFactory(
                missionReader, roomMissionReader, slotReader, seedGenerator, "commit-sha");
    }

    @Test
    @DisplayName("현재 Day/period eligibility와 방 사용 이력으로 일반 후보를 걸러 fallback 풀과 분리한다")
    void givenCatalogAndHistory_whenBuild_thenFiltersEligibleUnusedCandidates() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 11, 30);
        Room room = room(now.minusHours(3));
        RoomMissionSlot morning = slot(100L, room.id(), MissionPeriod.MORNING, now.plusMinutes(30));
        RoomMissionSlot afternoon = slot(101L, room.id(), MissionPeriod.AFTERNOON, now.plusHours(6));
        MissionRevision used = revision(1L, false);
        MissionRevision eligible = revision(2L, false);
        MissionRevision wrongPeriod = revision(3L, false);
        MissionRevision fallback = revision(9L, true);
        given(roomMissionReader.getByRoomId(room.id())).willReturn(List.of(
                new RoomMission(500L, room.id(), used.id(), now.minusHours(1), now, 0, now.minusHours(2))));
        given(slotReader.getByRoomId(room.id())).willReturn(List.of(morning, afternoon));
        given(missionReader.getActiveCatalog()).willReturn(List.of(
                entry(used, 1, MissionPeriod.MORNING),
                entry(eligible, 1, MissionPeriod.MORNING),
                entry(wrongPeriod, 1, MissionPeriod.AFTERNOON),
                entry(fallback, 1, MissionPeriod.MORNING)));
        given(seedGenerator.nextSeed()).willReturn(77L);

        MissionAssignmentContext context = factory.build(room, morning, now);

        assertThat(context.previousMissionRevisionIds()).containsExactly(1L);
        assertThat(context.candidates()).extracting(MissionRevision::id).containsExactly(2L);
        assertThat(context.fallbackCandidates()).extracting(MissionRevision::id).containsExactly(9L);
        assertThat(context.sequence()).isZero();
        assertThat(context.rngSeed()).isEqualTo(77L);
        assertThat(context.catalogVersion()).hasSize(64);
        assertThat(context.applicationCommitSha()).isEqualTo("commit-sha");
    }

    private MissionCatalogEntry entry(MissionRevision revision, int day, MissionPeriod period) {
        return new MissionCatalogEntry(
                revision, List.of(MissionRevisionEligibility.of(revision.id(), day, period)));
    }

    private Room room(LocalDateTime startsAt) {
        return new Room(10L, RoomStatus.ACTIVE, LocalDate.of(2026, 9, 2), 3, startsAt, null,
                startsAt, startsAt.plusHours(63), startsAt.plusHours(65),
                startsAt.plusHours(66), startsAt.plusHours(72));
    }

    private RoomMissionSlot slot(Long id, Long roomId, MissionPeriod period, LocalDateTime opensAt) {
        return new RoomMissionSlot(id, roomId, 1, period, opensAt, opensAt.plusHours(5), opensAt.minusDays(1));
    }

    private MissionRevision revision(Long id, boolean fallback) {
        return new MissionRevision(id, id, 1, "미션 " + id,
                MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "CLUSTER_" + id, new BigDecimal("0.8"), fallback, null);
    }
}
