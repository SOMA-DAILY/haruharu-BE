package org.son.haruharu.domain.mission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.mission.implement.MissionAssignmentContextSnapshot;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.MissionCandidateSnapshot;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomStateSnapshot;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionWriter;
import org.son.haruharu.domain.mission.storage.MissionAssignmentIncidentJpaRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:mission_assignment_concurrency_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MissionAssignmentConcurrencyIntegrationTest {

    @Autowired RoomWriter roomWriter;
    @Autowired RoomMissionSlotService slotService;
    @Autowired RoomMissionWriter roomMissionWriter;
    @Autowired RoomMissionReader roomMissionReader;
    @Autowired MissionAssignmentIncidentJpaRepository incidentRepository;

    @Test
    @DisplayName("동일 슬롯의 동시 저장은 UNIQUE 제약으로 한 건만 남고 incident를 만들지 않는다")
    void givenTwoConcurrentAssignments_whenPersistSameSlot_thenExactlyOneWins() throws Exception {
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 2, 9, 0);
        Room room = roomWriter.save(Room.createScheduled(
                startsAt, startsAt.plusHours(63), startsAt.plusHours(65),
                startsAt.plusHours(66), startsAt.plusHours(72), 3));
        RoomMissionSlot slot = slotService.createDefaultsForRoom(
                room.id(), 3, startsAt, startsAt.plusHours(63)).getFirst();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<Boolean> first = saveConcurrently(assignment(room, slot, 101L), ready, start);
        CompletableFuture<Boolean> second = saveConcurrently(assignment(room, slot, 102L), ready, start);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(roomMissionReader.getByRoomId(room.id())).hasSize(1);
        assertThat(incidentRepository.findByRoomMissionSlotId(slot.id())).isEmpty();
    }

    private CompletableFuture<Boolean> saveConcurrently(
            RoomMission assignment,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("concurrent assignment start timeout");
                }
                roomMissionWriter.save(assignment);
                return true;
            } catch (DataIntegrityViolationException duplicate) {
                return false;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("concurrent assignment interrupted", interrupted);
            }
        });
    }

    private RoomMission assignment(Room room, RoomMissionSlot slot, Long revisionId) {
        LocalDateTime assignedAt = slot.opensAt().minusMinutes(30);
        BigDecimal probability = new BigDecimal("0.500000000000");
        return RoomMission.assigned(
                room.id(), slot.id(), revisionId, slot.opensAt(), slot.closesAt(), 0,
                "RANDOM_V1", "catalog-sha", "context-v1", assignedAt,
                new MissionAssignmentContextSnapshot(
                        room.id(), slot.id(), slot.dayNumber(), slot.period().name(),
                        slot.opensAt(), slot.closesAt(), List.of(), RoomStateSnapshot.empty(6), assignedAt),
                "candidate-v1",
                new MissionCandidateSnapshot(List.of(
                        new MissionCandidateSnapshot.Candidate(revisionId, revisionId, 1, false, probability))),
                probability, UUID.randomUUID(), 1L, "SPLITTABLE_RANDOM_V1",
                MissionAssignmentRelaxationLevel.NONE, "test-sha", assignedAt, 0L);
    }
}
