package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.son.haruharu.domain.room.business.dto.TodayResponse.RewardStatus;
import org.son.haruharu.domain.room.implement.DailyUploadRewardReader;
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
    @Mock DailyUploadRewardReader dailyUploadRewardReader;

    private final UploadRewardProperties rewardProps =
            new UploadRewardProperties(Map.of(1, 2L, 2, 2L, 3, 1L));

    private RoomTodayService serviceAt(int hour) {
        ZonedDateTime fixed = ZonedDateTime.of(2026, 6, 26, hour, 0, 0, 0, SEOUL);
        Clock clock = Clock.fixed(fixed.toInstant(), SEOUL);
        return new RoomTodayService(roomReader, roomMemberReader, videoPostReader,
                dailyUploadRewardReader, rewardProps, clock);
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
        given(videoPostReader.getUploadedSlotIndexes(any(), anyInt())).willReturn(List.of());

        // 12시 → Day1 슬롯 중 12-15(slot2)만 열림
        TodayResponse result = serviceAt(12).getToday(roomId, userId);

        assertThat(result.currentDay()).isEqualTo(1);
        assertThat(result.totalSlots()).isEqualTo(5);
        assertThat(result.uploadedCount()).isZero();
        assertThat(result.slots()).extracting(TodayResponse.SlotView::openNow)
            .containsExactly(false, true, false, false, false);
    }

    @Test
    @DisplayName("슬롯별 uploaded는 요청 유저 본인의 업로드 여부로 채워지고 uploaded_count와 정합이 유지된다")
    void givenUploadedSlots_whenGetToday_thenSlotUploadedFlagsPersonalized() {
        Long userId = 1L;
        Long roomId = 100L;
        Long memberId = 200L;
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 26), 3,
            LocalDateTime.of(2026, 6, 26, 9, 0), null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(memberId, roomId, userId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        // 내가 슬롯1만 업로드, 슬롯2는 지나감(놓침) — 요청 멤버 id 기준으로 조회된다
        given(videoPostReader.getUploadedSlotIndexes(memberId, 1)).willReturn(List.of(1));

        TodayResponse result = serviceAt(15).getToday(roomId, userId);

        assertThat(result.slots()).extracting(TodayResponse.SlotView::uploaded)
            .containsExactly(true, false, false, false, false);
        assertThat(result.uploadedCount()).isEqualTo(1); // uploaded=true 개수와 정합
    }

    @Test
    @DisplayName("응답 now는 주입된 Clock 기준 시각이다(dev 가상 시계 반영, 카운트다운 기준점)")
    void givenFixedClock_whenGetToday_thenNowFromClock() {
        Long userId = 1L;
        Long roomId = 100L;
        Long memberId = 200L;
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 26), 3,
            LocalDateTime.of(2026, 6, 26, 9, 0), null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(memberId, roomId, userId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(videoPostReader.getUploadedSlotIndexes(any(), anyInt())).willReturn(List.of());

        TodayResponse result = serviceAt(12).getToday(roomId, userId);

        assertThat(result.now()).isEqualTo(LocalDateTime.of(2026, 6, 26, 12, 0));
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
        given(videoPostReader.getUploadedSlotIndexes(any(), anyInt())).willReturn(List.of());

        TodayResponse result = serviceAt(12).getToday(roomId, userId);

        assertThat(result.currentDay()).isZero();
        assertThat(result.slots()).isEmpty();
        assertThat(result.totalSlots()).isZero();
    }

    @Test
    @DisplayName("완주 보상 미지급 상태면 Day1 rewardAmount=2·rewardStatus=LOCKED을 반환한다")
    void givenNotRewarded_whenGetToday_thenRewardLocked() {
        Long userId = 1L;
        Long roomId = 100L;
        Long memberId = 200L;
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 26), 3,
            LocalDateTime.of(2026, 6, 26, 9, 0), null);
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(memberId, roomId, userId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(videoPostReader.getUploadedSlotIndexes(any(), anyInt())).willReturn(List.of(1, 2));
        given(dailyUploadRewardReader.isRewarded(memberId, 1)).willReturn(false);

        TodayResponse result = serviceAt(12).getToday(roomId, userId);

        assertThat(result.rewardAmount()).isEqualTo(2);
        assertThat(result.rewardStatus()).isEqualTo(RewardStatus.LOCKED);
    }

    @Test
    @DisplayName("완주 보상이 이미 지급됐으면 rewardStatus=EARNED을 반환한다")
    void givenRewarded_whenGetToday_thenRewardEarned() {
        Long userId = 1L;
        Long roomId = 100L;
        Long memberId = 200L;
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 26), 3,
            LocalDateTime.of(2026, 6, 26, 9, 0), null);
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(memberId, roomId, userId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(videoPostReader.getUploadedSlotIndexes(any(), anyInt())).willReturn(List.of(1, 2, 3, 4, 5));
        given(dailyUploadRewardReader.isRewarded(memberId, 1)).willReturn(true);

        TodayResponse result = serviceAt(12).getToday(roomId, userId);

        assertThat(result.rewardStatus()).isEqualTo(RewardStatus.EARNED);
        assertThat(result.rewardAmount()).isEqualTo(2);
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

    /** 타이머 대상 계산을 위한 공통 준비: startedAt 앵커로 currentDay를 파생시키는 ACTIVE 방. */
    private void givenActiveRoom(Long roomId, Long userId, Long memberId, LocalDate startedDate) {
        Room room = new Room(roomId, RoomStatus.ACTIVE, startedDate, 3,
            startedDate.atTime(9, 0), null);
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willReturn(new RoomMember(memberId, roomId, userId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(videoPostReader.getUploadedSlotIndexes(any(), anyInt())).willReturn(List.of());
    }

    @Test
    @DisplayName("Day1 슬롯 진행 중이면 다음 슬롯 시작 절대시각을 NEXT_SLOT 타입으로 반환한다")
    void givenDay1MidSlot_whenGetToday_thenNextSlotStart() {
        // startedAt 2026-06-26 09:00 → 당일 currentDay=1, 13시엔 다음 슬롯 시작 = 15:00
        givenActiveRoom(100L, 1L, 200L, LocalDate.of(2026, 6, 26));

        TodayResponse result = serviceAt(13).getToday(100L, 1L);

        assertThat(result.timerType()).isEqualTo(TodayResponse.TimerType.NEXT_SLOT);
        assertThat(result.timerTargetAt()).isEqualTo(LocalDateTime.of(2026, 6, 26, 15, 0));
    }

    @Test
    @DisplayName("Day1 마지막 슬롯(21-24) 중이면 다음 날 첫 슬롯(익일 09:00)을 NEXT_SLOT으로 반환한다")
    void givenDay1LastSlot_whenGetToday_thenNextDayFirstSlot() {
        givenActiveRoom(100L, 1L, 200L, LocalDate.of(2026, 6, 26));

        TodayResponse result = serviceAt(22).getToday(100L, 1L);

        assertThat(result.timerType()).isEqualTo(TodayResponse.TimerType.NEXT_SLOT);
        assertThat(result.timerTargetAt()).isEqualTo(LocalDateTime.of(2026, 6, 27, 9, 0));
    }

    @Test
    @DisplayName("Day3 마지막 슬롯(15-18) 중이면 최종선택 오픈(18:00)을 DEADLINE으로 반환한다")
    void givenDay3LastSlot_whenGetToday_thenSelectionOpenDeadline() {
        // startedAt 2026-06-24 09:00 → 2026-06-26엔 currentDay=3, 16시는 Day3 마지막 슬롯 중
        givenActiveRoom(100L, 1L, 200L, LocalDate.of(2026, 6, 24));

        TodayResponse result = serviceAt(16).getToday(100L, 1L);

        assertThat(result.timerType()).isEqualTo(TodayResponse.TimerType.DEADLINE);
        assertThat(result.timerTargetAt()).isEqualTo(LocalDateTime.of(2026, 6, 26, 18, 0));
    }

    @Test
    @DisplayName("Day3 18:00~22:00(최종선택 진행 중)이면 결과 발표(22:00)를 DEADLINE으로 반환한다")
    void givenDay3SelectionWindow_whenGetToday_thenResultDeadline() {
        givenActiveRoom(100L, 1L, 200L, LocalDate.of(2026, 6, 24));

        TodayResponse result = serviceAt(19).getToday(100L, 1L);

        assertThat(result.timerType()).isEqualTo(TodayResponse.TimerType.DEADLINE);
        assertThat(result.timerTargetAt()).isEqualTo(LocalDateTime.of(2026, 6, 26, 22, 0));
    }

    @Test
    @DisplayName("Day3 결과 발표(22:00) 이후면 타이머 대상이 없어 null을 반환한다")
    void givenDay3AfterResult_whenGetToday_thenNoTimer() {
        givenActiveRoom(100L, 1L, 200L, LocalDate.of(2026, 6, 24));

        TodayResponse result = serviceAt(23).getToday(100L, 1L);

        assertThat(result.timerTargetAt()).isNull();
        assertThat(result.timerType()).isNull();
    }

    @Test
    @DisplayName("Day3 정확히 18:00(경계)이면 최종선택이 열린 상태로 보아 결과 발표(22:00) DEADLINE을 반환한다")
    void givenDay3ExactlySelectionOpen_whenGetToday_thenResultDeadline() {
        givenActiveRoom(100L, 1L, 200L, LocalDate.of(2026, 6, 24));

        TodayResponse result = serviceAt(18).getToday(100L, 1L);

        assertThat(result.timerType()).isEqualTo(TodayResponse.TimerType.DEADLINE);
        assertThat(result.timerTargetAt()).isEqualTo(LocalDateTime.of(2026, 6, 26, 22, 0));
    }

    @Test
    @DisplayName("정확히 슬롯 시작 시각(Day1 15:00)이면 그 슬롯은 '다음'이 아니므로 다음 슬롯(18:00)을 반환한다")
    void givenExactSlotStart_whenGetToday_thenPointsToFollowingSlot() {
        givenActiveRoom(100L, 1L, 200L, LocalDate.of(2026, 6, 26));

        TodayResponse result = serviceAt(15).getToday(100L, 1L);

        assertThat(result.timerType()).isEqualTo(TodayResponse.TimerType.NEXT_SLOT);
        assertThat(result.timerTargetAt()).isEqualTo(LocalDateTime.of(2026, 6, 26, 18, 0));
    }

    @Test
    @DisplayName("PENDING 방은 타이머 대상이 없어 null을 반환한다")
    void givenPending_whenGetToday_thenNoTimer() {
        Room room = new Room(103L, RoomStatus.PENDING, LocalDate.now(), 3, null, null);
        given(roomMemberReader.getByRoomIdAndUserId(103L, 1L))
            .willReturn(new RoomMember(203L, 103L, 1L, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(103L)).willReturn(Optional.of(room));
        given(videoPostReader.getUploadedSlotIndexes(any(), anyInt())).willReturn(List.of());

        TodayResponse result = serviceAt(12).getToday(103L, 1L);

        assertThat(result.timerTargetAt()).isNull();
        assertThat(result.timerType()).isNull();
    }
}
