package org.son.haruharu.domain.mission.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.business.dto.RoomMissionResponse;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomMissionWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RoomMissionServiceTest {

    @Mock MissionAssigner missionAssigner;
    @Mock RoomMissionReader roomMissionReader;
    @Mock RoomMissionWriter roomMissionWriter;
    @Mock MissionReader missionReader;
    @Mock RoomMemberReader roomMemberReader;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T12:00:00Z"), ZoneId.of("Asia/Seoul"));

    RoomMissionService service;

    @BeforeEach
    void setUp() {
        service = new RoomMissionService(missionAssigner, roomMissionReader, roomMissionWriter,
                missionReader, roomMemberReader, clock);
    }

    private Room room(Long id) {
        LocalDateTime starts = LocalDateTime.of(2026, 6, 26, 9, 0);
        return new Room(id, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 26), 3, starts, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(2),
                starts.plusDays(2).plusHours(3), starts.plusDays(3));
    }

    private RoomMember member(Long roomId, Long userId) {
        return new RoomMember(200L, roomId, userId, "MALE", null, LocalDateTime.now());
    }

    @Test
    @DisplayName("미배정 방이면 assigner 결과를 저장한다")
    void givenUnassigned_whenAssignForRoom_thenSaves() {
        Room room = room(10L);
        given(roomMissionReader.isAssigned(10L)).willReturn(false);
        List<RoomMission> assigned = List.of(
                RoomMission.of(10L, 1L, LocalDateTime.now(clock), null, 0));
        given(missionAssigner.assign(room)).willReturn(assigned);

        service.assignForRoom(room);

        then(roomMissionWriter).should().saveAll(assigned);
    }

    @Test
    @DisplayName("이미 배정된 방이면 assigner를 호출하지 않는다(멱등)")
    void givenAlreadyAssigned_whenAssignForRoom_thenSkips() {
        Room room = room(10L);
        given(roomMissionReader.isAssigned(10L)).willReturn(true);

        service.assignForRoom(room);

        then(missionAssigner).should(never()).assign(any());
        then(roomMissionWriter).should(never()).saveAll(anyList());
    }

    @Test
    @DisplayName("assigner가 빈 목록을 주면 저장하지 않는다")
    void givenAssignerReturnsEmpty_whenAssignForRoom_thenNoSave() {
        Room room = room(10L);
        given(roomMissionReader.isAssigned(10L)).willReturn(false);
        given(missionAssigner.assign(room)).willReturn(List.of());

        service.assignForRoom(room);

        then(roomMissionWriter).should(never()).saveAll(anyList());
    }

    @Test
    @DisplayName("열린 미션 조회 시 문구를 붙여 sequence 순으로 반환한다")
    void givenOpenMissions_whenGetOpenMissions_thenReturnsWithDescription() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(10L, 1L));
        LocalDateTime now = LocalDateTime.now(clock);
        given(roomMissionReader.getOpenMissions(10L, now)).willReturn(List.of(
                new RoomMission(100L, 10L, 1L, now.minusHours(1), null, 0, now),
                new RoomMission(101L, 10L, 2L, now.minusHours(1), null, 1, now)));
        given(missionReader.getActiveCandidates()).willReturn(List.of(
                new Mission(1L, "오늘 먹은 음식", 5, true),
                new Mission(2L, "지금 풍경", 5, true)));

        List<RoomMissionResponse> result = service.getOpenMissions(10L, 1L);

        assertThat(result).extracting(RoomMissionResponse::roomMissionId).containsExactly(100L, 101L);
        assertThat(result).extracting(RoomMissionResponse::description)
                .containsExactly("오늘 먹은 음식", "지금 풍경");
    }

    @Test
    @DisplayName("문구가 비활성화된(카탈로그에 없는) 미션은 카드에서 제외한다")
    void givenDeactivatedMission_whenGetOpenMissions_thenExcluded() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willReturn(member(10L, 1L));
        LocalDateTime now = LocalDateTime.now(clock);
        given(roomMissionReader.getOpenMissions(10L, now)).willReturn(List.of(
                new RoomMission(100L, 10L, 1L, now.minusHours(1), null, 0, now),
                new RoomMission(101L, 10L, 99L, now.minusHours(1), null, 1, now))); // 99L은 카탈로그에 없음
        given(missionReader.getActiveCandidates()).willReturn(List.of(
                new Mission(1L, "오늘 먹은 음식", 5, true)));

        List<RoomMissionResponse> result = service.getOpenMissions(10L, 1L);

        assertThat(result).extracting(RoomMissionResponse::roomMissionId).containsExactly(100L);
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetOpenMissions_thenThrows() {
        given(roomMemberReader.getByRoomIdAndUserId(10L, 1L)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getOpenMissions(10L, 1L))
                .isInstanceOf(NotRoomMemberException.class);
    }
}
