package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.mission.implement.UploadSlotReader;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoomTodayServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock UploadSlotReader uploadSlotReader;
    @Mock MissionReader missionReader;
    @Mock VideoPostReader videoPostReader;

    RoomTodayService roomTodayService;

    private RoomTodayService serviceAt(int hour) {
        // 서울 기준 해당 시각(hour)으로 고정된 Clock
        ZonedDateTime fixed = ZonedDateTime.of(2026, 6, 26, hour, 0, 0, 0, SEOUL);
        Clock clock = Clock.fixed(fixed.toInstant(), SEOUL);
        return new RoomTodayService(roomReader, roomMemberReader, uploadSlotReader,
                missionReader, videoPostReader, clock);
    }

    @Test
    @DisplayName("ACTIVE Day1 조회 시 해당 Day 슬롯·미션 후보·업로드 진행을 반환하고 현재 시각의 슬롯이 열려 있다")
    void givenActiveDay1_whenGetToday_thenReturnsSlotsAndMissions() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID slotMidId = UUID.randomUUID();
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, 1, LocalDateTime.now(), null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(memberId, roomId, userId, "MALE", 0, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(uploadSlotReader.getByDay(1)).willReturn(List.of(
            new UploadSlot(UUID.randomUUID(), 1, 1, 8, 11),
            new UploadSlot(slotMidId, 1, 2, 11, 14)
        ));
        given(videoPostReader.countUploaded(any(), anyList())).willReturn(0L);
        given(missionReader.getActiveCandidates()).willReturn(List.of(
            new Mission(UUID.randomUUID(), "오늘 먹은 음식", 5, true)
        ));

        // 12시 → 11-14 슬롯 열림, 8-11 슬롯 닫힘
        TodayResponse result = serviceAt(12).getToday(roomId, userId);

        assertThat(result.currentDay()).isEqualTo(1);
        assertThat(result.totalSlots()).isEqualTo(2);
        assertThat(result.uploadedCount()).isZero();
        assertThat(result.missionCandidates()).hasSize(1);
        assertThat(result.slots()).extracting(TodayResponse.SlotView::openNow)
            .containsExactly(false, true);
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
        given(uploadSlotReader.getByDay(0)).willReturn(List.of());
        given(videoPostReader.countUploaded(any(), anyList())).willReturn(0L);
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
