package org.son.haruharu.admin.domain.dashboard.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.domain.dashboard.business.dto.DashboardMetricsResponse;
import org.son.haruharu.admin.domain.dashboard.business.dto.DashboardSummaryResponse;
import org.son.haruharu.admin.domain.dashboard.business.exception.AdminDashboardInvalidRangeException;
import org.son.haruharu.admin.domain.dashboard.implement.DashboardReader;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.implement.Room;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock DashboardReader dashboardReader;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T03:00:00Z"), ZoneOffset.UTC);

    AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(dashboardReader, clock);
    }

    private Room activeRoom(LocalDateTime startedAt) {
        return Room.create(LocalDate.of(2026, 7, 8), 3).started(startedAt);
    }

    @Test
    @DisplayName("진행중 방을 일차별로 집계하고, 매칭 대기 성비·오늘 가입 수를 요약으로 반환한다")
    void getSummary_aggregatesActiveRoomsByDayAndQueueAndSignups() {
        LocalDateTime now = LocalDateTime.now(clock);
        int dayStartHour = UploadSlot.dayStartHour();
        Room dayA1 = activeRoom(now);
        Room dayA2 = activeRoom(now);
        Room dayB = activeRoom(now.minusDays(1));
        int keyA = dayA1.currentDayAt(now, dayStartHour);
        int keyB = dayB.currentDayAt(now, dayStartHour);

        given(dashboardReader.activeRooms()).willReturn(List.of(dayA1, dayA2, dayB));
        given(dashboardReader.queuedCount()).willReturn(34L);
        given(dashboardReader.queuedCountByGender("FEMALE")).willReturn(19L);
        given(dashboardReader.queuedCountByGender("MALE")).willReturn(15L);
        given(dashboardReader.signupsSince(any())).willReturn(57L);

        DashboardSummaryResponse result = service.getSummary();

        assertThat(result.activeRooms().total()).isEqualTo(3);
        assertThat(result.activeRooms().byDay()).containsEntry(keyA, 2L).containsEntry(keyB, 1L);
        assertThat(result.matchingQueue().total()).isEqualTo(34L);
        assertThat(result.matchingQueue().female()).isEqualTo(19L);
        assertThat(result.matchingQueue().male()).isEqualTo(15L);
        assertThat(result.todaySignups()).isEqualTo(57L);
    }

    @Test
    @DisplayName("오늘 가입 집계는 오늘(KST) 00:00을 기준 시각으로 조회한다")
    void getSummary_signupsUseTodayStart() {
        given(dashboardReader.activeRooms()).willReturn(List.of());
        given(dashboardReader.queuedCount()).willReturn(0L);
        given(dashboardReader.queuedCountByGender(any())).willReturn(0L);
        given(dashboardReader.signupsSince(any())).willReturn(0L);

        service.getSummary();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(dashboardReader).signupsSince(captor.capture());
        assertThat(captor.getValue()).isEqualTo(LocalDate.now(clock).atStartOfDay());
    }

    @Test
    @DisplayName("metrics는 일자별 가입·매칭 추이와 성공률을 반환하고 빈 날짜는 0으로 채운다")
    void getMetrics_buildsTrendsWithSuccessRate() {
        LocalDate from = LocalDate.of(2026, 7, 8);
        LocalDate to = LocalDate.of(2026, 7, 10);
        given(dashboardReader.signupCountsByDay(any(), any()))
                .willReturn(Map.of(LocalDate.of(2026, 7, 8), 5L, LocalDate.of(2026, 7, 10), 3L));
        Map<MatchingQueueStatus, Long> day09 = new EnumMap<>(MatchingQueueStatus.class);
        day09.put(MatchingQueueStatus.MATCHED, 6L);
        day09.put(MatchingQueueStatus.EXPIRED, 2L);
        day09.put(MatchingQueueStatus.CANCELLED, 1L); // 성공률 분모 제외 대상
        given(dashboardReader.matchingCountsByDay(any(), any()))
                .willReturn(Map.of(LocalDate.of(2026, 7, 9), day09));

        DashboardMetricsResponse result = service.getMetrics(from, to);

        assertThat(result.range().from()).isEqualTo(from);
        assertThat(result.range().to()).isEqualTo(to);
        // 3일 연속 축
        assertThat(result.signups()).hasSize(3);
        assertThat(result.signups().get(0)).isEqualTo(new DashboardMetricsResponse.SignupPoint(from, 5L));
        assertThat(result.signups().get(1).count()).isZero(); // 07-09 빈날 0
        assertThat(result.signups().get(2).count()).isEqualTo(3L);
        // 07-09 매칭: applications = 6+2+1 = 9, 성공률 6/(6+2)=0.75
        DashboardMetricsResponse.MatchingPoint mid = result.matching().daily().get(1);
        assertThat(mid.applications()).isEqualTo(9L);
        assertThat(mid.matched()).isEqualTo(6L);
        assertThat(mid.expired()).isEqualTo(2L);
        assertThat(mid.successRate()).isEqualTo(0.75);
        // 빈 날은 성공률 null
        assertThat(result.matching().daily().get(0).successRate()).isNull();
        // overall
        assertThat(result.matching().overall().applications()).isEqualTo(9L);
        assertThat(result.matching().overall().successRate()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("from이 to보다 이후면 InvalidMetricsRangeException(400)")
    void getMetrics_fromAfterTo_throws() {
        assertThatThrownBy(() -> service.getMetrics(LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 10)))
                .isInstanceOf(AdminDashboardInvalidRangeException.class);
    }

    @Test
    @DisplayName("from/to 미지정 시 최근 14일 범위로 조회한다")
    void getMetrics_defaultsToLast14Days() {
        given(dashboardReader.signupCountsByDay(any(), any())).willReturn(Map.of());
        given(dashboardReader.matchingCountsByDay(any(), any())).willReturn(Map.of());

        DashboardMetricsResponse result = service.getMetrics(null, null);

        assertThat(result.range().to()).isEqualTo(LocalDate.of(2026, 7, 10));   // 오늘(clock)
        assertThat(result.range().from()).isEqualTo(LocalDate.of(2026, 6, 27)); // 오늘-13
        assertThat(result.signups()).hasSize(14);
        assertThat(result.matching().daily()).hasSize(14);
    }

    @Test
    @DisplayName("범위가 92일을 넘으면 from을 to-91로 clamp한다")
    void getMetrics_clampsRangeTo92Days() {
        given(dashboardReader.signupCountsByDay(any(), any())).willReturn(Map.of());
        given(dashboardReader.matchingCountsByDay(any(), any())).willReturn(Map.of());

        DashboardMetricsResponse result = service.getMetrics(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 10));

        assertThat(result.range().to()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(result.range().from()).isEqualTo(LocalDate.of(2026, 4, 10)); // to-91
        assertThat(result.signups()).hasSize(92);
    }
}
