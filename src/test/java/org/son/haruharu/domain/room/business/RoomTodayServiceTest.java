package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.video.implement.VideoPostReader;

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
    @Mock VideoPostReader videoPostReader;

    private RoomTodayService serviceAt(int hour) {
        ZonedDateTime fixed = ZonedDateTime.of(2026, 6, 26, hour, 0, 0, 0, SEOUL);
        Clock clock = Clock.fixed(fixed.toInstant(), SEOUL);
        return new RoomTodayService(roomReader, roomMemberReader, videoPostReader, clock);
    }

    @Test
    @DisplayName("ACTIVE Day1 조회 시 Day1 슬롯 5개·업로드 진행을 반환하고 12시엔 12-15 슬롯만 열려 있다")
    void givenActiveDay1_whenGetToday_thenReturnsSlots() {
        Long userId = 1L;
        Long roomId = 100L;
        Long memberId = 200L;
        // startedAt=당일 09:00 앵커 → serviceAt 기준(2026-06-26) currentDay=1로 파생
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 26), 3,
            LocalDateTime.of(2026, 6, 26, 9, 0), null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(memberId, roomId, userId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(videoPostReader.countUploaded(any(), anyInt())).willReturn(0L);

        // 12시 → Day1 슬롯 중 12-15(slot2)만 열림
        TodayResponse result = serviceAt(12).getToday(roomId, userId);

        assertThat(result.currentDay()).isEqualTo(1);
        assertThat(result.totalSlots()).isEqualTo(5);
        assertThat(result.uploadedCount()).isZero();
        assertThat(result.slots()).extracting(TodayResponse.SlotView::openNow)
            .containsExactly(false, true, false, false, false);
    }

    @Test
    @DisplayName("PENDING(currentDay=0) 방은 슬롯이 없어 빈 목록을 반환한다")
    void givenPendingRoom_whenGetToday_thenEmptySlots() {
        Long userId = 1L;
        Long roomId = 101L;
        Room room = new Room(roomId, RoomStatus.PENDING, LocalDate.now(), 3, null, null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(201L, roomId, userId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(videoPostReader.countUploaded(any(), anyInt())).willReturn(0L);

        TodayResponse result = serviceAt(12).getToday(roomId, userId);

        assertThat(result.currentDay()).isZero();
        assertThat(result.slots()).isEmpty();
        assertThat(result.totalSlots()).isZero();
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetToday_thenThrows() {
        Long userId = 1L;
        Long roomId = 102L;
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> serviceAt(12).getToday(roomId, userId))
            .isInstanceOf(NotRoomMemberException.class);
    }
}
