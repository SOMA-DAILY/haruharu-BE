package org.son.haruharu.admin.domain.dashboard.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.domain.dashboard.business.dto.DashboardSummaryResponse;
import org.son.haruharu.admin.domain.dashboard.implement.DashboardReader;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.implement.Room;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
}
