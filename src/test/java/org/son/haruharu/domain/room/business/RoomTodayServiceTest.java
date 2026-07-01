package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.video.implement.VideoPostReader;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoomTodayServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock MissionReader missionReader;
    @Mock VideoPostReader videoPostReader;

    private RoomTodayService serviceAt(int hour) {
        ZonedDateTime fixed = ZonedDateTime.of(2026, 6, 26, hour, 0, 0, 0, SEOUL);
        Clock clock = Clock.fixed(fixed.toInstant(), SEOUL);
        return new RoomTodayService(roomReader, roomMemberReader, missionReader, videoPostReader, clock);
    }

    @Test
    @DisplayName("ACTIVE Day1 조회 시 Day1 슬롯 5개·미션 후보·업로드 진행을 반환하고 12시엔 11-14 슬롯만 열려 있다")
    void givenActiveDay1_whenGetToday_thenReturnsSlotsAndMissions() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, 1, LocalDateTime.now(), null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(memberId, roomId, userId, "MALE", 0, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(videoPostReader.countUploaded(any(), anyInt())).willReturn(0L);
        given(missionReader.getActiveCandidates()).willReturn(List.of(
            new Mission(UUID.randomUUID(), "오늘 먹은 음식", 5, true)
        ));

        // 12시 → Day1 슬롯 중 11-14(slot2)만 열림
        TodayResponse result = serviceAt(12).getToday(roomId, userId);

        assertThat(result.currentDay()).isEqualTo(1);
        assertThat(result.totalSlots()).isEqualTo(5);
        assertThat(result.uploadedCount()).isZero();
        assertThat(result.missionCandidates()).hasSize(1);
        assertThat(result.slots()).extracting(TodayResponse.SlotView::openNow)
            .containsExactly(false, true, false, false, false);
    }

    @Test
    @DisplayName("PENDING(currentDay=0) 방은 슬롯이 없어 빈 목록을 반환한다")
    void givenPendingRoom_whenGetToday_thenEmptySlots() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Room room = new Room(roomId, RoomStatus.PENDING, LocalDate.now(), 3, 0, null, null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", 0, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(videoPostReader.countUploaded(any(), anyInt())).willReturn(0L);
        given(missionReader.getActiveCandidates()).willReturn(List.of());

        TodayResponse result = serviceAt(12).getToday(roomId, userId);

        assertThat(result.currentDay()).isZero();
        assertThat(result.slots()).isEmpty();
        assertThat(result.totalSlots()).isZero();
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetToday_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> serviceAt(12).getToday(roomId, userId))
            .isInstanceOf(NotRoomMemberException.class);
    }
}
