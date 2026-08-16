package org.son.haruharu.admin.domain.dashboard.business;

import org.son.haruharu.admin.domain.dashboard.business.dto.DashboardMetricsResponse;
import org.son.haruharu.admin.domain.dashboard.business.dto.DashboardSummaryResponse;
import org.son.haruharu.admin.domain.dashboard.business.exception.AdminDashboardInvalidRangeException;
import org.son.haruharu.admin.domain.dashboard.implement.DashboardReader;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.room.implement.Room;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private static final String GENDER_FEMALE = "FEMALE";
    private static final String GENDER_MALE = "MALE";
    private static final int DEFAULT_RANGE_DAYS = 14;
    private static final int MAX_RANGE_DAYS = 92;

    private final DashboardReader dashboardReader;
    private final Clock clock;

    public AdminDashboardService(DashboardReader dashboardReader, Clock clock) {
        this.dashboardReader = dashboardReader;
        this.clock = clock;
    }

    public DashboardSummaryResponse getSummary() {
        return new DashboardSummaryResponse(activeRooms(), matchingQueue(), todaySignups());
    }

    /**
     * 기간별 추이. from/to 미지정 시 최근 14일, from&gt;to면 400, 92일 초과 시 from을 to-91로 clamp.
     * 일자별 가입·매칭을 조회해 from~to 연속 날짜로 0 채우고 매칭 성공률을 파생한다.
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
        Map<LocalDate, Map<MatchingQueueStatus, Long>> matchingByDay =
                dashboardReader.matchingCountsByDay(fromStart, toEnd);

        List<DashboardMetricsResponse.SignupPoint> signups = new ArrayList<>();
        List<DashboardMetricsResponse.MatchingPoint> matchingDaily = new ArrayList<>();
        long totalApplications = 0;
        long totalMatched = 0;
        long totalExpired = 0;
        for (LocalDate date = resolvedFrom; !date.isAfter(resolvedTo); date = date.plusDays(1)) {
            signups.add(new DashboardMetricsResponse.SignupPoint(date, signupsByDay.getOrDefault(date, 0L)));

            Map<MatchingQueueStatus, Long> byStatus = matchingByDay.getOrDefault(date, Map.of());
            long matched = byStatus.getOrDefault(MatchingQueueStatus.MATCHED, 0L);
            long expired = byStatus.getOrDefault(MatchingQueueStatus.EXPIRED, 0L);
            long applications = byStatus.values().stream().mapToLong(Long::longValue).sum();
            matchingDaily.add(new DashboardMetricsResponse.MatchingPoint(
                    date, applications, matched, expired, successRate(matched, expired)));
            totalApplications += applications;
            totalMatched += matched;
            totalExpired += expired;
        }

        DashboardMetricsResponse.MatchingOverall overall = new DashboardMetricsResponse.MatchingOverall(
                totalApplications, totalMatched, totalExpired, successRate(totalMatched, totalExpired));
        return new DashboardMetricsResponse(
                new DashboardMetricsResponse.Range(resolvedFrom, resolvedTo),
                signups,
                new DashboardMetricsResponse.Matching(matchingDaily, overall));
    }

    /** 성공률 = matched/(matched+expired). 소수 4자리 반올림, 분모 0이면 null. */
    private static Double successRate(long matched, long expired) {
        long denominator = matched + expired;
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(matched)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .doubleValue();
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

    private DashboardSummaryResponse.MatchingQueue matchingQueue() {
        return new DashboardSummaryResponse.MatchingQueue(
                dashboardReader.queuedCount(),
                dashboardReader.queuedCountByGender(GENDER_FEMALE),
                dashboardReader.queuedCountByGender(GENDER_MALE));
    }

    private long todaySignups() {
        LocalDateTime todayStart = LocalDate.now(clock).atStartOfDay();
        return dashboardReader.signupsSince(todayStart);
    }
}
