package org.son.haruharu.admin.domain.dashboard.business;

import org.son.haruharu.admin.domain.dashboard.business.dto.DashboardMetricsResponse;
import org.son.haruharu.admin.domain.dashboard.business.dto.DashboardSummaryResponse;
import org.son.haruharu.admin.domain.dashboard.business.exception.AdminDashboardInvalidRangeException;
import org.son.haruharu.admin.domain.dashboard.implement.DashboardReader;
import org.son.haruharu.domain.room.implement.Room;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** 대시보드 집계. 요약 KPI(스냅샷)와 metrics(기간별 시계열 추이)를 제공한다. */
@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final int DEFAULT_RANGE_DAYS = 14;
    private static final int MAX_RANGE_DAYS = 92;

    private final DashboardReader dashboardReader;
    private final Clock clock;

    public AdminDashboardService(DashboardReader dashboardReader, Clock clock) {
        this.dashboardReader = dashboardReader;
        this.clock = clock;
    }

    public DashboardSummaryResponse getSummary() {
        return new DashboardSummaryResponse(activeRooms(), todaySignups());
    }

    /**
     * 기간별 추이. from/to 미지정 시 최근 14일, from&gt;to면 400, 92일 초과 시 from을 to-91로 clamp.
     * 일자별 가입 수를 조회해 from~to 연속 날짜로 빈 날짜를 0으로 채운다.
     */
    public DashboardMetricsResponse getMetrics(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to != null ? to : LocalDate.now(clock);
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new AdminDashboardInvalidRangeException();
        }
        if (ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) > MAX_RANGE_DAYS - 1L) {
            resolvedFrom = resolvedTo.minusDays(MAX_RANGE_DAYS - 1L);
        }

        LocalDateTime fromStart = resolvedFrom.atStartOfDay();
        LocalDateTime toEnd = resolvedTo.plusDays(1).atStartOfDay();
        Map<LocalDate, Long> signupsByDay = dashboardReader.signupCountsByDay(fromStart, toEnd);
        List<DashboardMetricsResponse.SignupPoint> signups = new ArrayList<>();
        for (LocalDate date = resolvedFrom; !date.isAfter(resolvedTo); date = date.plusDays(1)) {
            signups.add(new DashboardMetricsResponse.SignupPoint(date, signupsByDay.getOrDefault(date, 0L)));
        }

        return new DashboardMetricsResponse(
                new DashboardMetricsResponse.Range(resolvedFrom, resolvedTo),
                signups);
    }

    private DashboardSummaryResponse.ActiveRooms activeRooms() {
        List<Room> rooms = dashboardReader.activeRooms();
        LocalDateTime now = LocalDateTime.now(clock);
        // 방마다 현재 일차(1~totalDays, 최종=totalDays)를 파생해 집계한다.
        Map<Integer, Long> byDay = rooms.stream()
                .collect(Collectors.groupingBy(
                        room -> room.currentDayAt(now),
                        TreeMap::new,
                        Collectors.counting()));
        return new DashboardSummaryResponse.ActiveRooms(rooms.size(), byDay);
    }

    private long todaySignups() {
        LocalDateTime todayStart = LocalDate.now(clock).atStartOfDay();
        return dashboardReader.signupsSince(todayStart);
    }
}
