package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.MissionMemberStateWriter;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.business.exception.RoomMissionNotOpenException;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MissionMemberStateServiceTest {

    @Mock RoomMissionReader roomMissionReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock MissionMemberStateInitializer initializer;
    @Mock MissionMemberStateWriter stateWriter;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-02T00:10:00Z"), ZoneId.of("Asia/Seoul"));
    private MissionMemberStateService service;

    @BeforeEach
    void setUp() {
        service = new MissionMemberStateService(
                roomMissionReader, roomMemberReader, initializer, stateWriter, clock);
    }

    @Test
    @DisplayName("열린 방 미션 impression은 대상을 catch-up한 뒤 원자적으로 기록한다")
    void givenOpenMissionTarget_whenImpress_thenInitializesAndRecords() {
        LocalDateTime now = LocalDateTime.now(clock);
        RoomMission mission = mission(1L, now.minusMinutes(10), now.plusHours(1));
        RoomMember member = new RoomMember(20L, 1L, 30L, "MALE", null, null, now.minusDays(1));
        given(roomMissionReader.getById(10L)).willReturn(mission);
        given(roomMemberReader.getByRoomIdAndUserId(1L, 30L)).willReturn(member);
        given(stateWriter.impress(10L, 20L, now)).willReturn(1);

        service.impress(1L, 10L, 30L);

        verify(initializer).initializeTarget(mission, member);
        verify(stateWriter).impress(10L, 20L, now);
    }

    @Test
    @DisplayName("다른 방 미션 또는 닫힌 미션 impression은 상태를 쓰지 않는다")
    void givenForeignOrClosedMission_whenImpress_thenRejectsWithoutWrite() {
        LocalDateTime now = LocalDateTime.now(clock);
        given(roomMemberReader.getByRoomIdAndUserId(1L, 30L))
                .willReturn(new RoomMember(20L, 1L, 30L, "MALE", null, null, now.minusDays(1)));
        given(roomMissionReader.getById(10L))
                .willReturn(mission(99L, now.minusHours(2), now.minusHours(1)));

        assertThatThrownBy(() -> service.impress(1L, 10L, 30L))
                .isInstanceOf(RoomMissionNotOpenException.class);
        verify(stateWriter, never()).impress(10L, 20L, now);
    }

    @Test
    @DisplayName("비멤버 쓰기 요청은 미션 존재 여부를 조회하기 전에 인가에서 거절한다")
    void givenNonMember_whenImpress_thenRejectsBeforeMissionLookup() {
        given(roomMemberReader.getByRoomIdAndUserId(1L, 30L))
                .willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.impress(1L, 10L, 30L))
                .isInstanceOf(NotRoomMemberException.class);

        verify(roomMissionReader, never()).getById(10L);
    }

    private RoomMission mission(Long roomId, LocalDateTime opensAt, LocalDateTime closesAt) {
        return new RoomMission(10L, roomId, 100L, opensAt, closesAt, 0, opensAt.minusHours(1));
    }
}
