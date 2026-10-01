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
    @DisplayName("오늘 화면은 현재 슬롯만 조회하고 반환한다")
    void givenCurrentSlot_whenGetToday_thenReturnsCurrentOnly() {
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(activeRoom()));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S22)))
                .willReturn(List.of(S22));
        given(videoPostReader.getUploadedSlotStarts(200L, List.of(S22))).willReturn(List.of());

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.slots()).extracting(TodayResponse.SlotView::slotStart)
                .containsExactly(S22);
        assertThat(result.slots()).extracting(TodayResponse.SlotView::openNow)
                .containsExactly(true);
        assertThat(result.totalSlots()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 노출 창이 모두 비어도 빈 현재 슬롯은 유지한다")
    void givenNoVisibleSlots_whenGetToday_thenKeepsCurrentSlot() {
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(activeRoom()));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S22)))
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
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S22)))
                .willReturn(List.of(S22));
        given(videoPostReader.getUploadedSlotStarts(eq(200L), anyList())).willReturn(List.of(S22));

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.slots()).extracting(TodayResponse.SlotView::uploaded)
                .containsExactly(true);
        assertThat(result.uploadedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("ACTIVE 진행 중이면 다음 슬롯 시작 시각을 NEXT_SLOT 타입으로 반환한다")
    void givenActiveMidSlot_whenGetToday_thenNextSlotStart() {
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(activeRoom()));
        given(roomMemberReader.getByRoomId(100L))
                .willReturn(List.of(member(200L, 1L), member(201L, 2L)));
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S22)))
                .willReturn(List.of(S22));
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
        given(videoPostReader.getVisibleSlotStarts(List.of(200L, 201L), List.of(S22)))
                .willReturn(List.of(S22));
        given(videoPostReader.getUploadedSlotStarts(eq(200L), anyList())).willReturn(List.of());

        TodayResponse result = serviceAt(DATE.atTime(22, 10)).getToday(100L, 1L);

        assertThat(result.timerType()).isEqualTo(TimerType.DEADLINE);
        assertThat(result.timerTargetAt()).isEqualTo(DATE.atTime(23, 0));
    }

    @Test
    @DisplayName("SELECTING(최종선택 진행 중)이면 결과 발표 시각을 DEADLINE으로 반환하고 현재 슬롯은 없다")
    void givenSelecting_whenGetToday_thenResultDeadlineAndNoOpenSlot() {
        // opensAt 20:00, resultAt 22:00, now 20:30 → statusAt=SELECTING
        Room room = new Room(100L, RoomStatus.ACTIVE, DATE, 3, STARTS, null,
                STARTS, DATE.atTime(20, 0), DATE.atTime(21, 0),
                DATE.atTime(22, 0), DATE.atTime(23, 0));
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(room));
        // 최종선택 오픈 이후 시작 슬롯은 창에서 제외되어 window가 비므로 getUploadedSlotStarts는 호출되지 않는다

        TodayResponse result = serviceAt(DATE.atTime(20, 30)).getToday(100L, 1L);

        assertThat(result.status()).isEqualTo(RoomStatus.SELECTING.name());
        assertThat(result.timerType()).isEqualTo(TimerType.DEADLINE);
        assertThat(result.timerTargetAt()).isEqualTo(DATE.atTime(22, 0));
        assertThat(result.slots()).noneMatch(TodayResponse.SlotView::openNow); // 촬영 불가
    }

    @Test
    @DisplayName("최종선택 시작 시 이전 슬롯은 만료되고 오늘 슬롯은 비어 있다")
    void givenSelectionStarted_whenGetToday_thenNoSlots() {
        Room room = new Room(100L, RoomStatus.ACTIVE, DATE, 3, STARTS, null,
                STARTS, DATE.atTime(22, 0), DATE.atTime(23, 0),
                DATE.plusDays(1).atTime(0, 0), DATE.plusDays(1).atTime(1, 0));
        given(roomMemberReader.getByRoomIdAndUserId(100L, 1L)).willReturn(member(200L, 1L));
        given(roomReader.findById(100L)).willReturn(Optional.of(room));

        TodayResponse result = serviceAt(DATE.atTime(22, 0)).getToday(100L, 1L);

        assertThat(result.slots()).isEmpty();
        assertThat(result.totalSlots()).isZero();
        assertThat(result.uploadedCount()).isZero();
        org.mockito.Mockito.verifyNoInteractions(videoPostReader);
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
