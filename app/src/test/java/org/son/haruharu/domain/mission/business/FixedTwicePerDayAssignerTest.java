package org.son.haruharu.domain.mission.business;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FixedTwicePerDayAssignerTest {

    @Mock MissionReader missionReader;

    FixedTwicePerDayAssigner assigner;

    private static final LocalDateTime STARTS = LocalDateTime.of(2026, 6, 26, 19, 0); // 저녁 시작 방

    @BeforeEach
    void setUp() {
        assigner = new FixedTwicePerDayAssigner(missionReader);
    }

    private Room room(Long id, int totalDays, LocalDateTime startsAt) {
        return new Room(id, RoomStatus.ACTIVE, startsAt == null ? LocalDate.of(2026, 6, 26) : startsAt.toLocalDate(),
                totalDays, startsAt, null, startsAt,
                startsAt == null ? null : startsAt.plusDays(2), startsAt == null ? null : startsAt.plusDays(2).plusHours(2),
                startsAt == null ? null : startsAt.plusDays(2).plusHours(3), startsAt == null ? null : startsAt.plusDays(3));
    }

    private List<MissionRevision> pool(int n) {
        return java.util.stream.IntStream.rangeClosed(1, n)
                .mapToObj(i -> revision((long) i, "미션" + i))
                .toList();
    }

    private MissionRevision revision(Long id, String description) {
        return new MissionRevision(id, id, 1, description,
                MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "CLUSTER_" + id, new BigDecimal("0.8"), false, null);
    }

    @Test
    @DisplayName("3일 방은 하루 2개씩 총 6개 미션을 starts_at 상대 오전(+3h)·오후(+9h) 시각으로 배정한다")
    void given3DayRoom_whenAssign_then6MissionsAtRelativeTimes() {
        given(missionReader.getActiveCandidates()).willReturn(pool(8));

        List<RoomMission> result = assigner.assign(room(10L, 3, STARTS));

        assertThat(result).hasSize(6);
        // Day1 오전=starts+3h, 오후=starts+9h / Day2=+24h 기준 / Day3=+48h 기준
        assertThat(result).extracting(RoomMission::opensAt).containsExactly(
                STARTS.plusHours(3), STARTS.plusHours(9),
                STARTS.plusHours(27), STARTS.plusHours(33),
                STARTS.plusHours(51), STARTS.plusHours(57));
        assertThat(result).extracting(RoomMission::sequence).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(result).allSatisfy(rm -> {
            assertThat(rm.roomId()).isEqualTo(10L);
            assertThat(rm.closesAt()).isNull(); // 무기한 열림(MVP)
        });
        // 방 안 미션 문구(missionId) 중복 없음
        assertThat(result).extracting(RoomMission::missionRevisionId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("후보 풀이 필요 수보다 적으면 있는 만큼만 배정한다")
    void givenSmallPool_whenAssign_thenAssignsOnlyAvailable() {
        given(missionReader.getActiveCandidates()).willReturn(pool(3)); // 6개 필요한데 3개뿐

        List<RoomMission> result = assigner.assign(room(10L, 3, STARTS));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(RoomMission::sequence).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("후보가 없으면 빈 목록을 반환한다")
    void givenEmptyPool_whenAssign_thenEmpty() {
        given(missionReader.getActiveCandidates()).willReturn(List.of());

        assertThat(assigner.assign(room(10L, 3, STARTS))).isEmpty();
    }

    @Test
    @DisplayName("스케줄 시각이 없는 구 모델 방은 배정하지 않는다")
    void givenLegacyRoom_whenAssign_thenEmpty() {
        assertThat(assigner.assign(room(10L, 3, null))).isEmpty();
    }
}
