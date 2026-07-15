package org.son.haruharu.admin.domain.dashboard.storage;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대시보드 집계 조회. admin은 core Reader를 컴포넌트 스캔하지 않으므로, 필요한 카운트 쿼리를
 * core 엔티티(@EntityScan으로 매핑된 room/matching/user)에 대해 JPQL로 직접 수행한다.
 * 모든 엔티티는 소프트삭제(@SoftDelete)라 Hibernate가 deleted_at is null을 자동 부가한다.
 */
@Repository
@RequiredArgsConstructor
public class DashboardQueryRepository {

    private final EntityManager entityManager;

    public List<Room> findRoomsByStatus(RoomStatus status) {
        return entityManager.createQuery(
                        "select r from RoomEntity r where r.status = :status", RoomEntity.class)
                .setParameter("status", status)
                .getResultList().stream()
                .map(RoomEntity::toDomain)
                .toList();
    }

    public long countQueueByStatus(MatchingQueueStatus status) {
        return entityManager.createQuery(
                        "select count(q) from MatchingQueueEntity q where q.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    public long countQueueByStatusAndGender(MatchingQueueStatus status, String gender) {
        return entityManager.createQuery(
                        "select count(q) from MatchingQueueEntity q where q.status = :status and q.gender = :gender", Long.class)
                .setParameter("status", status)
                .setParameter("gender", gender)
                .getSingleResult();
    }

    public long countUsersCreatedAfter(LocalDateTime from) {
        return entityManager.createQuery(
                        "select count(u) from UserEntity u where u.createdAt >= :from", Long.class)
                .setParameter("from", from)
                .getSingleResult();
    }

    /**
     * 일자별 가입 수. [fromStart, toEnd) 반개구간 내 createdAt을 날짜로 잘라 집계한다.
     * cast(x as date)는 H2(PostgreSQL 모드)·PostgreSQL 공통 동작. 빈 날짜는 결과에 없으므로 Service가 0으로 채운다.
     */
    public Map<LocalDate, Long> signupCountsByDay(LocalDateTime fromStart, LocalDateTime toEnd) {
        List<Object[]> rows = entityManager.createQuery(
                        "select cast(u.createdAt as date), count(u) from UserEntity u "
                                + "where u.createdAt >= :fromStart and u.createdAt < :toEnd "
                                + "group by cast(u.createdAt as date)", Object[].class)
                .setParameter("fromStart", fromStart)
                .setParameter("toEnd", toEnd)
                .getResultList();
        Map<LocalDate, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(toLocalDate(row[0]), (Long) row[1]);
        }
        return result;
    }

    /**
     * 일자별·상태별 매칭 큐 수. queuedAt을 날짜로 잘라 (일자, status)로 집계한다.
     * 반환: 일자 → (상태 → 수). Service가 성사/만료/신청·성공률을 파생한다.
     */
    public Map<LocalDate, Map<MatchingQueueStatus, Long>> matchingCountsByDay(LocalDateTime fromStart, LocalDateTime toEnd) {
        List<Object[]> rows = entityManager.createQuery(
                        "select cast(q.queuedAt as date), q.status, count(q) from MatchingQueueEntity q "
                                + "where q.queuedAt >= :fromStart and q.queuedAt < :toEnd "
                                + "group by cast(q.queuedAt as date), q.status", Object[].class)
                .setParameter("fromStart", fromStart)
                .setParameter("toEnd", toEnd)
                .getResultList();
        Map<LocalDate, Map<MatchingQueueStatus, Long>> result = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate date = toLocalDate(row[0]);
            MatchingQueueStatus status = (MatchingQueueStatus) row[1];
            result.computeIfAbsent(date, key -> new EnumMap<>(MatchingQueueStatus.class)).put(status, (Long) row[2]);
        }
        return result;
    }

    /** cast(timestamp as date) 결과 타입이 드라이버(H2/PG)에 따라 LocalDate/java.sql.Date로 갈려 방어적 변환. */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        throw new IllegalStateException("예상치 못한 날짜 타입: " + value.getClass());
    }
}
