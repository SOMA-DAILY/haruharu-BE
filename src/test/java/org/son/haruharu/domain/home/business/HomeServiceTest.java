package org.son.haruharu.domain.home.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.dto.WalletResponse;
import org.son.haruharu.domain.home.business.dto.HomeResponse;
import org.son.haruharu.domain.matching.business.MatchingService;
import org.son.haruharu.domain.matching.business.dto.QueueStatusResponse;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.dto.RoomResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock RoomService roomService;
    @Mock MatchingService matchingService;
    @Mock CoinService coinService;

    private final Long userId = 1L;

    private HomeService serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"));
        return new HomeService(roomService, matchingService, coinService, clock);
    }

    private void stubDefaults(long balance, QueueStatusResponse queue, RoomResponse room) {
        given(coinService.getWallet(userId))
                .willReturn(new WalletResponse(balance, balance, 0, LocalDateTime.now()));
        given(matchingService.getQueueStatus(userId)).willReturn(queue);
        given(roomService.findMyRoom(userId)).willReturn(Optional.ofNullable(room));
    }

    @Test
    @DisplayName("큐도 방도 없으면 status NONE, 잔액과 오늘 22:00 확정 시각이 반환된다")
    void givenNothing_whenGetHome_thenNoneWithTodayConfirmAt() {
        // 2026-07-05 10:00 KST
        HomeService service = serviceAt("2026-07-05T01:00:00Z");
        stubDefaults(12L, QueueStatusResponse.none(), null);

        HomeResponse result = service.getHome(userId);

        assertThat(result.pointBalance()).isEqualTo(12L);
        assertThat(result.matching().status()).isEqualTo("NONE");
        assertThat(result.matching().confirmAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 5, 22, 0));
        assertThat(result.activeRoom()).isNull();
    }

    @Test
    @DisplayName("22:00 이후에는 다음 날 22:00이 확정 시각으로 반환된다")
    void givenAfterConfirmHour_whenGetHome_thenTomorrowConfirmAt() {
        // 2026-07-05 23:00 KST
        HomeService service = serviceAt("2026-07-05T14:00:00Z");
        stubDefaults(0L, QueueStatusResponse.none(), null);

        HomeResponse result = service.getHome(userId);

        assertThat(result.matching().confirmAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 6, 22, 0));
    }

    @Test
    @DisplayName("큐 대기 중이면 QUEUED 상태와 queued_at이 그대로 노출된다")
    void givenQueued_whenGetHome_thenQueuedStatus() {
        HomeService service = serviceAt("2026-07-05T01:00:00Z");
        LocalDateTime queuedAt = LocalDateTime.of(2026, 7, 5, 9, 30);
        stubDefaults(7L, new QueueStatusResponse("QUEUED", queuedAt), null);

        HomeResponse result = service.getHome(userId);

        assertThat(result.matching().status()).isEqualTo("QUEUED");
        assertThat(result.matching().queuedAt()).isEqualTo(queuedAt);
        assertThat(result.activeRoom()).isNull();
    }

    @Test
    @DisplayName("진행 중(ACTIVE) 방이 있으면 방 요약과 시작·종료 시각(09:00 경계)이 반환된다")
    void givenActiveRoom_whenGetHome_thenRoomSummaryWithBoundaries() {
        HomeService service = serviceAt("2026-07-05T01:00:00Z");
        LocalDate scheduled = LocalDate.of(2026, 7, 4);
        RoomResponse room = new RoomResponse(10L, "ACTIVE", scheduled, 3, 2,
                scheduled.atTime(9, 0), 6);
        stubDefaults(3L, QueueStatusResponse.none(), room);

        HomeResponse result = service.getHome(userId);

        assertThat(result.activeRoom().roomId()).isEqualTo(10L);
        assertThat(result.activeRoom().status()).isEqualTo("ACTIVE");
        assertThat(result.activeRoom().currentDay()).isEqualTo(2);
        assertThat(result.activeRoom().startsAt()).isEqualTo(LocalDateTime.of(2026, 7, 4, 9, 0));
        assertThat(result.activeRoom().endsAt()).isEqualTo(LocalDateTime.of(2026, 7, 7, 9, 0));
    }

    @Test
    @DisplayName("확정 후 시작 전(PENDING) 방은 currentDay 0과 시작 예정 시각으로 반환된다")
    void givenPendingRoom_whenGetHome_thenPendingSummary() {
        HomeService service = serviceAt("2026-07-05T14:00:00Z");
        LocalDate scheduled = LocalDate.of(2026, 7, 6);
        RoomResponse room = new RoomResponse(11L, "PENDING", scheduled, 3, 0, null, 6);
        stubDefaults(0L, QueueStatusResponse.none(), room);

        HomeResponse result = service.getHome(userId);

        assertThat(result.activeRoom().status()).isEqualTo("PENDING");
        assertThat(result.activeRoom().currentDay()).isZero();
        assertThat(result.activeRoom().startsAt()).isEqualTo(LocalDateTime.of(2026, 7, 6, 9, 0));
    }
}
