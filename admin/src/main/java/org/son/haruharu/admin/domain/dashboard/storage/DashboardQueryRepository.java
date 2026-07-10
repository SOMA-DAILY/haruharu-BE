package org.son.haruharu.admin.domain.dashboard.storage;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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
}
