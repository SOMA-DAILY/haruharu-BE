package org.son.haruharu.admin.domain.dashboard.implement;

import org.son.haruharu.admin.domain.dashboard.storage.DashboardQueryRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 대시보드 집계 읽기 협력자. 트랜잭션 경계는 Service가 갖는다. */
@Component
public class DashboardReader {

    private final DashboardQueryRepository dashboardQueryRepository;

    public DashboardReader(DashboardQueryRepository dashboardQueryRepository) {
        this.dashboardQueryRepository = dashboardQueryRepository;
    }

    /** 진행중(ACTIVE) 방 목록. 일차 파생은 Service가 currentDayAt으로 계산한다. */
    public List<Room> activeRooms() {
        return dashboardQueryRepository.findRoomsByStatus(RoomStatus.ACTIVE);
    }

    /** 기준 시각 이후 가입한 유저 수. */
    public long signupsSince(LocalDateTime from) {
        return dashboardQueryRepository.countUsersCreatedAfter(from);
    }

    /** [fromStart, toEnd) 구간의 일자별 가입 수. 빈 날짜는 맵에 없음(Service가 0 채움). */
    public Map<LocalDate, Long> signupCountsByDay(LocalDateTime fromStart, LocalDateTime toEnd) {
        return dashboardQueryRepository.signupCountsByDay(fromStart, toEnd);
    }
}
