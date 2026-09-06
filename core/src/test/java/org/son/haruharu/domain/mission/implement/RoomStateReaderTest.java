package org.son.haruharu.domain.mission.implement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoomStateReaderTest {

    private static final Long ROOM_ID = 1L;
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Mock RoomMemberReader roomMemberReader;
    @Mock MissionOutcomeReader outcomeReader;

    RoomStateReader roomStateReader;

    @BeforeEach
    void setUp() {
        roomStateReader = new RoomStateReader(roomMemberReader, outcomeReader);
    }

    @Test
    @DisplayName("이전 미션이 없으면 outcome 을 조회하지 않고 멤버 수만 담는다")
    void givenNoPreviousMission_whenSnapshot_thenReturnsEmptyWithMemberCount() {
        // given
        given(roomMemberReader.getByRoomId(ROOM_ID)).willReturn(members(6));

        // when
        RoomStateSnapshot snapshot = roomStateReader.snapshot(ROOM_ID, List.of());

        // then
        assertThat(snapshot.memberCount()).isEqualTo(6);
        assertThat(snapshot.closedMissionCount()).isZero();
        assertThat(snapshot.averageActivationCoverage()).isNull();
        verifyNoInteractions(outcomeReader);
    }

    @Test
    @DisplayName("이전 미션은 있으나 확정된 outcome 이 아직 없으면 빈 상태로 둔다")
    void givenPreviousMissionWithoutOutcome_whenSnapshot_thenReturnsEmpty() {
        // given — 시간창이 아직 안 닫혀 outcome 이 확정되지 않은 경우
        given(roomMemberReader.getByRoomId(ROOM_ID)).willReturn(members(6));
        given(outcomeReader.findConfirmed(any())).willReturn(List.of());

        // when
        RoomStateSnapshot snapshot = roomStateReader.snapshot(ROOM_ID, List.of(roomMission(10L)));

        // then
        assertThat(snapshot.closedMissionCount()).isZero();
        assertThat(snapshot.lastActivationCoverage()).isNull();
    }

    @Test
    @DisplayName("확정된 outcome 들의 평균과 가장 최근 값을 담는다")
    void givenConfirmedOutcomes_whenSnapshot_thenAveragesAndTakesLatest() {
        // given — closesAt 순서가 뒤섞여 들어와도 시간순으로 정렬돼야 한다
        given(roomMemberReader.getByRoomId(ROOM_ID)).willReturn(members(6));
        given(outcomeReader.findConfirmed(any())).willReturn(List.of(
                outcome(BASE.plusHours(12), new BigDecimal("0.2000"), 2),
                outcome(BASE, new BigDecimal("0.5000"), 3),
                outcome(BASE.plusHours(6), new BigDecimal("0.8000"), 5)));

        // when
        RoomStateSnapshot snapshot = roomStateReader.snapshot(
                ROOM_ID, List.of(roomMission(10L), roomMission(11L), roomMission(12L)));

        // then
        assertThat(snapshot.closedMissionCount()).isEqualTo(3);
        assertThat(snapshot.averageActivationCoverage())
                .isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(snapshot.lastActivationCoverage())
                .as("가장 늦게 닫힌 미션의 값")
                .isEqualByComparingTo(new BigDecimal("0.2000"));
        assertThat(snapshot.activatedMemberCount())
                .as("한 번이라도 도달한 최대 활성 인원")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("나누어떨어지지 않는 평균은 소수점 넷째 자리까지 반올림한다")
    void givenIndivisibleAverage_whenSnapshot_thenScaledToFour() {
        given(roomMemberReader.getByRoomId(ROOM_ID)).willReturn(members(6));
        given(outcomeReader.findConfirmed(any())).willReturn(List.of(
                outcome(BASE, new BigDecimal("0.3333"), 2),
                outcome(BASE.plusHours(6), new BigDecimal("0.3333"), 2),
                outcome(BASE.plusHours(12), new BigDecimal("0.3334"), 2)));

        RoomStateSnapshot snapshot = roomStateReader.snapshot(
                ROOM_ID, List.of(roomMission(10L), roomMission(11L), roomMission(12L)));

        assertThat(snapshot.averageActivationCoverage().scale()).isEqualTo(4);
        assertThat(snapshot.averageActivationCoverage())
                .isEqualByComparingTo(new BigDecimal("0.3333"));
    }

    // --- fixtures ---

    private List<RoomMember> members(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> RoomMember.of(ROOM_ID, 100L + index, "MALE", null, BASE.minusDays(1)))
                .toList();
    }

    private RoomMission roomMission(Long id) {
        return new RoomMission(id, ROOM_ID, 1L, BASE, BASE.plusHours(5), 0, BASE);
    }

    private MissionOutcome outcome(LocalDateTime closesAt, BigDecimal coverage, int activated) {
        return new MissionOutcome(
                null, 10L, MissionOutcome.METRIC_SCHEMA_V1,
                6, 6, activated, activated, 0, 0,
                coverage, new BigDecimal("1.0000"), closesAt, closesAt.plusMinutes(5), closesAt);
    }
}
