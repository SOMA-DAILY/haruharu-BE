package org.son.haruharu.domain.mission.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.business.dto.RoomMissionResponse;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomMissionWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoomMissionServiceTest {

    @Mock RoomMissionReader roomMissionReader;
    @Mock MissionReader missionReader;
    @Mock RoomMemberReader roomMemberReader;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T12:00:00Z"), ZoneId.of("Asia/Seoul"));

    RoomMissionService service;

    @BeforeEach
    void setUp() {
        service = new RoomMissionService(roomMissionReader, missionReader, roomMemberReader, clock);
    }

    private RoomMember member(Long roomId, Long userId) {
        return new RoomMember(200L, roomId, userId, "MALE", null, LocalDateTime.now());
    }

    @Test
    @DisplayName("열린 미션 조회 시 문구를 붙여 sequence 순으로 반환한다")
    void givenOpenMissions_whenGetOpenMissions_thenReturnsWithDescription() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(10L, 1L));
        LocalDateTime now = LocalDateTime.now(clock);
        given(roomMissionReader.getOpenMissions(10L, now)).willReturn(List.of(
                new RoomMission(100L, 10L, 1L, now.minusHours(1), null, 0, now),
                new RoomMission(101L, 10L, 2L, now.minusHours(1), null, 1, now)));
        given(missionReader.getRevisionsByIds(Set.of(1L, 2L))).willReturn(List.of(
                revision(1L, "오늘 먹은 음식"),
                revision(2L, "지금 풍경")));

        List<RoomMissionResponse> result = service.getOpenMissions(10L, 1L);

        assertThat(result).extracting(RoomMissionResponse::roomMissionId).containsExactly(100L, 101L);
        assertThat(result).extracting(RoomMissionResponse::description)
                .containsExactly("오늘 먹은 음식", "지금 풍경");
    }

    @Test
    @DisplayName("논리 미션이 비활성화되어도 배정 당시 revision 문구는 계속 반환한다")
    void givenDeactivatedMission_whenGetOpenMissions_thenPreservesAssignedRevision() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(10L, 1L));
        LocalDateTime now = LocalDateTime.now(clock);
        given(roomMissionReader.getOpenMissions(10L, now)).willReturn(List.of(
                new RoomMission(100L, 10L, 1L, now.minusHours(1), null, 0, now),
                new RoomMission(101L, 10L, 99L, now.minusHours(1), null, 1, now)));
        given(missionReader.getRevisionsByIds(Set.of(1L, 99L))).willReturn(List.of(
                revision(1L, "오늘 먹은 음식"),
                revision(99L, "비활성화 전에 배정된 문구")));

        List<RoomMissionResponse> result = service.getOpenMissions(10L, 1L);

        assertThat(result).extracting(RoomMissionResponse::roomMissionId).containsExactly(100L, 101L);
        assertThat(result).extracting(RoomMissionResponse::description)
                .containsExactly("오늘 먹은 음식", "비활성화 전에 배정된 문구");
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetOpenMissions_thenThrows() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getOpenMissions(10L, 1L))
                .isInstanceOf(NotRoomMemberException.class);
    }

    private MissionRevision revision(Long id, String description) {
        return new MissionRevision(id, id, 1, description,
                MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "CLUSTER_" + id, new BigDecimal("0.8"), false, null);
    }
}
