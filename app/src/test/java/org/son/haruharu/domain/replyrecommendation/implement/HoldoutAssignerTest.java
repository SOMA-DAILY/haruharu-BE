package org.son.haruharu.domain.replyrecommendation.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomComposition;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;

@ExtendWith(MockitoExtension.class)
class HoldoutAssignerTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Mock HoldoutAssignmentRepository repository;
    @Mock RoomReader rooms;
    @Mock RoomMemberReader members;

    private HoldoutAssigner assigner(double ratio) {
        return new HoldoutAssigner(new ReplyHoldoutProperties(ratio, "salt"), repository, rooms, members);
    }

    private static Room cohortRoom(long id, Long cohortId) {
        return new Room(id, RoomStatus.ACTIVE, LocalDate.now(), 3, null, null, null, null, null, null, null,
                new RoomComposition(cohortId, null, null, null), true);
    }

    private static Map<Long, Double> hashes(double... values) {
        Map<Long, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            map.put((long) i + 1, values[i]);
        }
        return map;
    }

    @Test
    @DisplayName("같은 salt, 묶음 키, 사용자면 같은 해시가 나오고 [0, 1) 안이다. 기수가 다르면 달라진다")
    void givenSameInput_whenHashed_thenStableAndInRange() {
        double a = HoldoutAssigner.hash("salt", "cohort:9", 42L);
        assertThat(HoldoutAssigner.hash("salt", "cohort:9", 42L)).isEqualTo(a);
        assertThat(a).isGreaterThanOrEqualTo(0).isLessThan(1);
        assertThat(HoldoutAssigner.hash("salt", "cohort:10", 42L)).isNotEqualTo(a);
        assertThat(HoldoutAssigner.groupKey(cohortRoom(5L, 9L), 5L)).isEqualTo("cohort:9");
        assertThat(HoldoutAssigner.groupKey(cohortRoom(5L, null), 5L)).isEqualTo("room:5");
        assertThat(HoldoutAssigner.groupKey(null, 5L)).isEqualTo("room:5");
    }

    @Test
    @DisplayName("짝수 방은 해시 오름차순 앞 절반 TREATMENT, 뒤 절반 HOLDOUT")
    void givenEvenRoom_whenSplit_thenHalfHalf() {
        var arms = HoldoutAssigner.split(hashes(0.9, 0.1, 0.6, 0.3), 0.5);
        assertThat(arms).containsEntry(2L, ExperimentArm.TREATMENT).containsEntry(4L, ExperimentArm.TREATMENT)
                .containsEntry(3L, ExperimentArm.HOLDOUT).containsEntry(1L, ExperimentArm.HOLDOUT);
    }

    @Test
    @DisplayName("홀수 방의 가운데 1명은 해시가 0.5 미만이면 TREATMENT, 이상이면 HOLDOUT")
    void givenOddRoom_whenSplit_thenMiddleByHash() {
        assertThat(HoldoutAssigner.split(hashes(0.1, 0.2, 0.45, 0.7, 0.8), 0.5))
                .containsEntry(3L, ExperimentArm.TREATMENT)
                .containsEntry(1L, ExperimentArm.TREATMENT).containsEntry(2L, ExperimentArm.TREATMENT)
                .containsEntry(4L, ExperimentArm.HOLDOUT).containsEntry(5L, ExperimentArm.HOLDOUT);
        assertThat(HoldoutAssigner.split(hashes(0.1, 0.2, 0.55, 0.7, 0.8), 0.5))
                .containsEntry(3L, ExperimentArm.HOLDOUT);
    }

    @Test
    @DisplayName("비율 0 이면 NONE 이고 표를 읽거나 쓰지 않는다")
    void givenRatioZero_whenArmOf_thenNoneWithoutWrites() {
        assertThat(assigner(0).armOf(1L, 2L, NOW)).isEqualTo(ExperimentArm.NONE);
        verifyNoInteractions(repository, rooms, members);
    }

    @Test
    @DisplayName("561 SQL 이 없으면 비율이 켜져 있어도 NONE 이고 배정하지 않는다")
    void givenSchemaMissing_whenArmOf_thenNone() {
        given(repository.schemaReady()).willReturn(false);
        assertThat(assigner(0.5).armOf(1L, 2L, NOW)).isEqualTo(ExperimentArm.NONE);
        then(repository).should(never()).insertAll(any(), any());
    }

    @Test
    @DisplayName("이미 배정된 사람은 그 그룹을 그대로 돌려주고 다시 계산하지 않는다")
    void givenExisting_whenArmOf_thenKept() {
        given(repository.schemaReady()).willReturn(true);
        given(repository.findArm(1L, 2L)).willReturn(Optional.of(ExperimentArm.HOLDOUT));
        assertThat(assigner(0.5).armOf(1L, 2L, NOW)).isEqualTo(ExperimentArm.HOLDOUT);
        then(repository).should(never()).insertAll(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("방의 첫 요청이면 멤버 전원을 한 번에 배정해 넣고, 그 뒤 들어온 사람은 해시 ≥ 1 − ratio 면 HOLDOUT")
    void givenFirstRequestThenLateJoiner_whenArmOf_thenWholeRoomThenSingle() {
        given(repository.schemaReady()).willReturn(true);
        given(rooms.findById(1L)).willReturn(Optional.of(cohortRoom(1L, 9L)));
        given(members.getByRoomId(1L)).willReturn(List.of(member(10L), member(11L), member(12L), member(13L)));
        given(repository.findArm(1L, 10L)).willReturn(Optional.empty(), Optional.empty(), Optional.of(ExperimentArm.TREATMENT));
        given(repository.roomAssigned(1L)).willReturn(false);

        assertThat(assigner(0.5).armOf(1L, 10L, NOW)).isEqualTo(ExperimentArm.TREATMENT);
        ArgumentCaptor<List<HoldoutAssignment>> first = ArgumentCaptor.forClass(List.class);
        then(repository).should().insertAll(first.capture(), any());
        assertThat(first.getValue()).hasSize(4).allMatch(a -> a.groupKey().equals("cohort:9"));
        assertThat(first.getValue().stream().filter(a -> a.arm() == ExperimentArm.HOLDOUT)).hasSize(2);

        given(repository.findArm(1L, 99L)).willReturn(Optional.empty(), Optional.empty(), Optional.of(ExperimentArm.HOLDOUT));
        given(repository.roomAssigned(1L)).willReturn(true);
        assigner(0.5).armOf(1L, 99L, NOW);
        ArgumentCaptor<List<HoldoutAssignment>> late = ArgumentCaptor.forClass(List.class);
        then(repository).should(org.mockito.Mockito.times(2)).insertAll(late.capture(), any());
        HoldoutAssignment single = late.getAllValues().get(1).get(0);
        assertThat(late.getAllValues().get(1)).hasSize(1);
        assertThat(single.arm()).isEqualTo(single.hash() >= 0.5 ? ExperimentArm.HOLDOUT : ExperimentArm.TREATMENT);
        then(repository).should(org.mockito.Mockito.times(2)).lockRoom(1L);
    }

    @Test
    @DisplayName("잠근 뒤 다른 요청이 먼저 배정했으면 그 값을 쓰고 다시 넣지 않는다")
    void givenRaceLost_whenArmOf_thenUsesWinner() {
        given(repository.schemaReady()).willReturn(true);
        given(repository.findArm(1L, 10L)).willReturn(Optional.empty(), Optional.of(ExperimentArm.HOLDOUT));
        assertThat(assigner(0.5).armOf(1L, 10L, NOW)).isEqualTo(ExperimentArm.HOLDOUT);
        then(repository).should().lockRoom(1L);
        then(repository).should(never()).insertAll(any(), any());
    }

    private static RoomMember member(long userId) {
        return new RoomMember(null, 1L, userId, "MALE", "m", null, null);
    }
}
