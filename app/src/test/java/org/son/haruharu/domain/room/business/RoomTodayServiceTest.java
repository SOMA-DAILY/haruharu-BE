package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.son.haruharu.domain.room.business.dto.TodayResponse.TimerType;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoomTodayServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate DATE = LocalDate.of(2026, 6, 26);
    private static final LocalDateTime STARTS = DATE.atTime(20, 0);
    private static final LocalDateTime S20 = DATE.atTime(20, 0);
    private static final LocalDateTime S21 = DATE.atTime(21, 0);
    private static final LocalDateTime S22 = DATE.atTime(22, 0);

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock VideoPostReader videoPostReader;

    private RoomTodayService serviceAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
        return new RoomTodayService(roomReader, roomMemberReader, videoPostReader, clock);
    }

    /** starts_at 20:00 새 모델 ACTIVE 방(최종선택 2일 뒤 20:00, 결과 그 2시간 뒤, 종료 3일 뒤). */
    private Room activeRoom() {
        return new Room(100L, RoomStatus.ACTIVE, DATE, 3, STARTS, null,
                STARTS, STARTS.plusDays(2), STARTS.plusDays(2).plusHours(1),
                STARTS.plusDays(2).plusHours(2), STARTS.plusDays(3));
    }

    private RoomMember member(Long memberId, Long userId) {
        return new RoomMember(memberId, 100L, userId, "MALE", null, null, LocalDateTime.now());
    }

    @Test
    @DisplayName("공개 영상이 없는 지난 슬롯은 제외하고 빈 현재 슬롯은 유지한다")
    void givenOnlyOneVisiblePastSlot_whenGetToday_thenOmitsEmptyPastAndKeepsCurrent() {
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(activeRoom()));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S20, S21, S22)))
                .willReturn(List.of(S21));
        given(videoPostReader.getUploadedSlotStarts(200L, List.of(S21, S22))).willReturn(List.of());

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.slots()).extracting(TodayResponse.SlotView::slotStart)
                .containsExactly(S21, S22);
        assertThat(result.slots()).extracting(TodayResponse.SlotView::openNow)
                .containsExactly(false, true);
        assertThat(result.totalSlots()).isEqualTo(2);
    }

    @Test
    @DisplayName("현재 노출 창이 모두 비어도 빈 현재 슬롯은 유지한다")
    void givenNoVisibleSlots_whenGetToday_thenKeepsCurrentSlot() {
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(activeRoom()));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S20, S21, S22)))
                .willReturn(List.of());
        given(videoPostReader.getUploadedSlotStarts(200L, List.of(S22))).willReturn(List.of());

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.slots()).extracting(TodayResponse.SlotView::slotStart)
                .containsExactly(S22);
        assertThat(result.slots()).extracting(TodayResponse.SlotView::openNow)
                .containsExactly(true);
    }

    @Test
    @DisplayName("슬롯별 uploaded는 요청 유저 본인의 업로드 여부로 채워지고 uploaded_count와 정합이 유지된다")
    void givenUploadedSlots_whenGetToday_thenPersonalized() {
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(activeRoom()));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S20, S21, S22)))
                .willReturn(List.of(S20, S21, S22));
        given(videoPostReader.getUploadedSlotStarts(eq(200L), anyList())).willReturn(List.of(S21));

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.slots()).extracting(TodayResponse.SlotView::uploaded)
                .containsExactly(false, true, false);
        assertThat(result.uploadedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("ACTIVE 진행 중이면 다음 슬롯 시작 시각을 NEXT_SLOT 타입으로 반환한다")
    void givenActiveMidSlot_whenGetToday_thenNextSlotStart() {
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(activeRoom()));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S20, S21, S22)))
                .willReturn(List.of(S20, S21, S22));
        given(videoPostReader.getUploadedSlotStarts(eq(200L), anyList())).willReturn(List.of());

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.timerType()).isEqualTo(TimerType.NEXT_SLOT);
        assertThat(result.timerTargetAt()).isEqualTo(DATE.atTime(23, 0)); // 22:00 슬롯 다음 = 23:00
    }

    @Test
    @DisplayName("다음 슬롯이 최종선택 오픈 이후면 최종선택 오픈 시각을 DEADLINE으로 반환한다")
    void givenNextSlotAfterSelectionOpen_whenGetToday_thenSelectionDeadline() {
        // 최종선택 오픈을 23:00로 당긴 방: now=22:10 → 다음 슬롯 23:00 == opensAt → DEADLINE@23:00
        Room room = new Room(100L, RoomStatus.ACTIVE, DATE, 3, STARTS, null,
                STARTS, DATE.atTime(23, 0), DATE.atTime(23, 30),
                DATE.plusDays(1).atTime(0, 0), DATE.plusDays(1).atTime(1, 0));
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S20, S21, S22)))
                .willReturn(List.of(S20, S21, S22));
        given(videoPostReader.getUploadedSlotStarts(eq(200L), anyList())).willReturn(List.of());

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.timerType()).isEqualTo(TimerType.DEADLINE);
        assertThat(result.timerTargetAt()).isEqualTo(DATE.atTime(23, 0));
    }

    @Test
    @DisplayName("SELECTING 중에도 방 종료 전이면 현재 슬롯이 열려 있고 다음 슬롯을 NEXT_SLOT으로 센다(#436)")
    void givenSelecting_whenGetToday_thenCurrentSlotOpenAndNextSlotTimer() {
        // opensAt 20:00, resultAt 다음날 01:00, now 22:10 → SELECTING. 다음 슬롯 23:00이 결과 발표보다 먼저
        Room room = new Room(100L, RoomStatus.ACTIVE, DATE, 3, STARTS, null,
                STARTS, DATE.atTime(20, 0), DATE.plusDays(1).atTime(0, 0),
                DATE.plusDays(1).atTime(1, 0), DATE.plusDays(2).atTime(0, 0));
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S20, S21, S22)))
                .willReturn(List.of(S21));
        given(videoPostReader.getUploadedSlotStarts(200L, List.of(S21, S22))).willReturn(List.of());

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.status()).isEqualTo(RoomStatus.SELECTING.name());
        assertThat(result.slots()).extracting(TodayResponse.SlotView::slotStart)
                .containsExactly(S21, S22); // 빈 지난 슬롯(20시)은 제외, 빈 현재 슬롯은 유지
        assertThat(result.slots()).filteredOn(TodayResponse.SlotView::openNow)
                .extracting(TodayResponse.SlotView::slotStart).containsExactly(S22);
        assertThat(result.timerType()).isEqualTo(TimerType.NEXT_SLOT);
        assertThat(result.timerTargetAt()).isEqualTo(DATE.atTime(23, 0));
    }

    @Test
    @DisplayName("SELECTING 중 결과 발표가 다음 슬롯보다 먼저면 결과 발표 시각을 DEADLINE으로 센다")
    void givenSelectingResultBeforeNextSlot_whenGetToday_thenResultDeadline() {
        // resultAt 22:30, now 22:10 → 다음 슬롯(23:00)보다 발표가 먼저
        Room room = new Room(100L, RoomStatus.ACTIVE, DATE, 3, STARTS, null,
                STARTS, DATE.atTime(21, 0), DATE.atTime(22, 20),
                DATE.atTime(22, 30), DATE.plusDays(1).atTime(0, 0));
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S20, S21, S22)))
                .willReturn(List.of());
        given(videoPostReader.getUploadedSlotStarts(eq(200L), anyList())).willReturn(List.of());

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.timerType()).isEqualTo(TimerType.DEADLINE);
        assertThat(result.timerTargetAt()).isEqualTo(DATE.atTime(22, 30));
    }

    @Test
    @DisplayName("결과 발표 후 방 종료 전이면 다음 슬롯을 NEXT_SLOT으로 센다")
    void givenAfterResultBeforeEnd_whenGetToday_thenNextSlotTimer() {
        Room room = new Room(100L, RoomStatus.SELECTING, DATE, 3, STARTS, null,
                STARTS, DATE.atTime(20, 30), DATE.atTime(21, 0),
                DATE.atTime(21, 30), DATE.plusDays(1).atTime(0, 0));
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S20, S21, S22)))
                .willReturn(List.of());
        given(videoPostReader.getUploadedSlotStarts(eq(200L), anyList())).willReturn(List.of());

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.timerType()).isEqualTo(TimerType.NEXT_SLOT);
        assertThat(result.timerTargetAt()).isEqualTo(DATE.atTime(23, 0));
    }

    @Test
    @DisplayName("방 종료(ends_at) 후에는 현재 슬롯이 없고 종료 이후 시작 슬롯은 창에서 제외된다")
    void givenEnded_whenGetToday_thenNoCurrentAndCappedAtEnd() {
        // ends_at 22:00, now 22:10 → ENDED. 창 [20,21,22] 중 22:00(종료 이후 시작)은 제외
        Room room = new Room(100L, RoomStatus.SELECTING, DATE, 3, STARTS, null,
                STARTS, DATE.atTime(20, 30), DATE.atTime(21, 0),
                DATE.atTime(21, 30), DATE.atTime(22, 0));
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S20, S21)))
                .willReturn(List.of(S21));
        given(videoPostReader.getUploadedSlotStarts(200L, List.of(S21))).willReturn(List.of());

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.slots()).extracting(TodayResponse.SlotView::slotStart).containsExactly(S21);
        assertThat(result.slots()).noneMatch(TodayResponse.SlotView::openNow);
        assertThat(result.timerType()).isNull();
    }

    @Test
    @DisplayName("시작 전(PENDING) 방은 슬롯이 없고 타이머 대상도 없다")
    void givenPending_whenGetToday_thenEmptyAndNoTimer() {
        LocalDateTime starts = DATE.plusDays(1).atTime(20, 0);
        Room room = new Room(101L, RoomStatus.PENDING, DATE, 3, null, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(1),
                starts.plusDays(2).plusHours(2), starts.plusDays(3));
        given(roomMemberReader.getByRoomIdAndUserId(101L, 1L)).willReturn(member(201L, 1L));
        given(roomReader.findById(101L)).willReturn(Optional.of(room));

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(101L, 1L);

        assertThat(result.slots()).isEmpty();
        assertThat(result.totalSlots()).isZero();
        assertThat(result.timerTargetAt()).isNull();
        assertThat(result.timerType()).isNull();
    }

    @Test
    @DisplayName("응답 now는 주입된 Clock 기준 시각이다")
    void givenFixedClock_whenGetToday_thenNowFromClock() {
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(activeRoom()));
        given(videoPostReader.getUploadedSlotStarts(eq(200L), anyList())).willReturn(List.of());

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.now()).isEqualTo(DATE.atTime(22, 10));
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetToday_thenThrows() {
        given(roomMemberReader.getByRoomIdAndUserId(any(), any()))
                .willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> serviceAt(DATE.atTime(22, 10)).getToday(102L, 1L))
                .isInstanceOf(NotRoomMemberException.class);
    }
}
