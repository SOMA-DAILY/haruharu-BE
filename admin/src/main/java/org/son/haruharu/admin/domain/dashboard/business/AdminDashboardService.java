package org.son.haruharu.admin.domain.dashboard.business;

import org.son.haruharu.admin.domain.dashboard.business.dto.DashboardSummaryResponse;
import org.son.haruharu.admin.domain.dashboard.implement.DashboardReader;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.implement.Room;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** 대시보드 요약 KPI 집계. 진행중 방(일차별)·매칭 대기(성비)·오늘 가입. */
@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final String GENDER_FEMALE = "FEMALE";
    private static final String GENDER_MALE = "MALE";

    private final DashboardReader dashboardReader;
    private final Clock clock;

    public AdminDashboardService(DashboardReader dashboardReader, Clock clock) {
        this.dashboardReader = dashboardReader;
        this.clock = clock;
    }

    public DashboardSummaryResponse getSummary() {
        return new DashboardSummaryResponse(activeRooms(), matchingQueue(), todaySignups());
    }

    private DashboardSummaryResponse.ActiveRooms activeRooms() {
        List<Room> rooms = dashboardReader.activeRooms();
        LocalDateTime now = LocalDateTime.now(clock);
        int dayStartHour = UploadSlot.dayStartHour();
        // 방마다 현재 일차(1~totalDays, 최종=totalDays)를 파생해 집계한다.
        Map<Integer, Long> byDay = rooms.stream()
                .collect(Collectors.groupingBy(
                        room -> room.currentDayAt(now, dayStartHour),
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
