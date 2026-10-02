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
import org.son.haruharu.domain.home.business.dto.HomeResponse;
import org.son.haruharu.domain.room.business.MatchResultService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.dto.PendingMatchResultResponse;
import org.son.haruharu.domain.room.business.dto.PendingMatchResultResponse.Outcome;
import org.son.haruharu.domain.room.business.dto.RoomResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock RoomService roomService;
    @Mock MatchResultService matchResultService;

    private final Long userId = 1L;

    private HomeService serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"));
        return new HomeService(roomService, matchResultService, clock);
    }

    private void stub(RoomResponse room, PendingMatchResultResponse pending) {
        given(roomService.findMyRoom(userId)).willReturn(Optional.ofNullable(room));
        given(matchResultService.findPendingResult(userId)).willReturn(Optional.ofNullable(pending));
    }

    private RoomResponse activeRoom() {
        LocalDate scheduled = LocalDate.of(2026, 7, 4);
        return new RoomResponse(10L, "ACTIVE", scheduled, 3, 2,
                scheduled.atTime(19, 0), scheduled.atTime(19, 0), scheduled.plusDays(3).atTime(9, 0), 6);
    }

    @Test
    @DisplayName("참여 중인 방이 없으면 activeRoom은 null이다(초대코드 입력 유도)")
    void givenNoRoom_whenGetHome_thenActiveRoomNull() {
        HomeService service = serviceAt("2026-07-05T01:00:00Z");
        stub(null, null);

        HomeResponse result = service.getHome(userId);

        assertThat(result.activeRoom()).isNull();
        assertThat(result.pendingResult()).isNull();
    }

    @Test
    @DisplayName("진행 중(ACTIVE) 방이 있으면 방 요약과 starts_at·ends_at이 반환된다")
    void givenActiveRoom_whenGetHome_thenRoomSummary() {
        HomeService service = serviceAt("2026-07-05T01:00:00Z");
        stub(activeRoom(), null);

        HomeResponse result = service.getHome(userId);

        assertThat(result.activeRoom().roomId()).isEqualTo(10L);
        assertThat(result.activeRoom().status()).isEqualTo("ACTIVE");
        assertThat(result.activeRoom().currentDay()).isEqualTo(2);
        assertThat(result.activeRoom().startsAt()).isEqualTo(LocalDateTime.of(2026, 7, 4, 19, 0));
        assertThat(result.activeRoom().endsAt()).isEqualTo(LocalDateTime.of(2026, 7, 7, 9, 0));
    }

    @Test
    @DisplayName("발표 후 매칭 결과가 있으면 pendingResult로 그대로 내려간다")
    void givenPendingResult_whenGetHome_thenExposed() {
        HomeService service = serviceAt("2026-07-05T01:00:00Z");
        PendingMatchResultResponse pending = new PendingMatchResultResponse(
                12L, Outcome.MATCHED, 34L, "하루", LocalDateTime.of(2026, 7, 4, 22, 0));
        stub(null, pending);

        HomeResponse result = service.getHome(userId);

        assertThat(result.pendingResult()).isEqualTo(pending);
    }

    @Test
    @DisplayName("방과 발표 결과가 함께 있으면 둘 다 노출된다")
    void givenRoomAndResult_whenGetHome_thenBoth() {
        HomeService service = serviceAt("2026-07-05T01:00:00Z");
        PendingMatchResultResponse pending = new PendingMatchResultResponse(
                12L, Outcome.FAILED, null, null, LocalDateTime.of(2026, 7, 4, 22, 0));
        stub(activeRoom(), pending);

        HomeResponse result = service.getHome(userId);

        assertThat(result.activeRoom().roomId()).isEqualTo(10L);
        assertThat(result.pendingResult().outcome()).isEqualTo(Outcome.FAILED);
    }

    @Test
    @DisplayName("응답 now는 주입된 Clock 기준 시각이다")
    void givenFixedClock_whenGetHome_thenNowFromClock() {
        HomeService service = serviceAt("2026-07-05T01:00:00Z"); // 10:00 KST
        stub(null, null);

        HomeResponse result = service.getHome(userId);

        assertThat(result.now()).isEqualTo(LocalDateTime.of(2026, 7, 5, 10, 0));
    }
}
