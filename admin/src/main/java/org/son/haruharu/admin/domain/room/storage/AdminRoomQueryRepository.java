package org.son.haruharu.admin.domain.room.storage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 어드민 세션(방) 조회. admin 전용 JPQL over core RoomEntity/RoomMemberEntity.
 * status/scheduledDate는 선택 필터라 조건을 동적으로 조립한다. 소프트삭제는 Hibernate가 자동 필터.
 */
@Repository
@RequiredArgsConstructor
public class AdminRoomQueryRepository {

    private final EntityManager entityManager;

    public List<Room> findRooms(RoomStatus status, LocalDate scheduledDate, int offset, int limit) {
        return findRooms(status, scheduledDate, null, offset, limit);
    }

    public List<Room> findRooms(RoomStatus status, LocalDate scheduledDate, Boolean isTest, int offset, int limit) {
        StringBuilder jpql = new StringBuilder("select r from RoomEntity r where 1 = 1");
        appendFilters(jpql, status, scheduledDate, isTest);
        jpql.append(" order by r.id desc");
        TypedQuery<RoomEntity> query = entityManager.createQuery(jpql.toString(), RoomEntity.class);
        bindFilters(query, status, scheduledDate, isTest);
        return query.setFirstResult(offset).setMaxResults(limit)
                .getResultList().stream().map(RoomEntity::toDomain).toList();
    }

    public long countRooms(RoomStatus status, LocalDate scheduledDate) {
        return countRooms(status, scheduledDate, null);
    }

    public long countRooms(RoomStatus status, LocalDate scheduledDate, Boolean isTest) {
        StringBuilder jpql = new StringBuilder("select count(r) from RoomEntity r where 1 = 1");
        appendFilters(jpql, status, scheduledDate, isTest);
        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        bindFilters(query, status, scheduledDate, isTest);
        return query.getSingleResult();
    }

    public Optional<Room> findRoomById(Long roomId) {
        return entityManager.createQuery("select r from RoomEntity r where r.id = :roomId", RoomEntity.class)
                .setParameter("roomId", roomId)
                .getResultList().stream().findFirst().map(RoomEntity::toDomain);
    }

    /** 쓰기 command의 404 매핑용 스칼라 존재 확인. RoomEntity를 persistence context에 올리지 않는다. */
    public boolean existsRoomById(Long roomId) {
        return entityManager.createQuery(
                        "select count(r.id) from RoomEntity r where r.id = :roomId", Long.class)
                .setParameter("roomId", roomId)
                .getSingleResult() > 0;
    }

    /** 여러 방의 참여자를 한 번에 조회(목록 성비·인원 집계용, N+1 방지). */
    public List<RoomMember> findMembersByRoomIds(List<Long> roomIds) {
        if (roomIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "select m from RoomMemberEntity m where m.roomId in :roomIds", RoomMemberEntity.class)
                .setParameter("roomIds", roomIds)
                .getResultList().stream().map(RoomMemberEntity::toDomain).toList();
    }

    public List<RoomMember> findMembersByRoomId(Long roomId) {
        return entityManager.createQuery(
                        "select m from RoomMemberEntity m where m.roomId = :roomId order by m.id", RoomMemberEntity.class)
                .setParameter("roomId", roomId)
                .getResultList().stream().map(RoomMemberEntity::toDomain).toList();
    }

    /** 참여자 프로필(닉네임·지역) 일괄 조회. 상세의 참여자 카드 enrich용. */
    public List<UserProfile> findProfilesByUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "select p from UserProfileEntity p where p.userId in :userIds", UserProfileEntity.class)
                .setParameter("userIds", userIds)
                .getResultList().stream().map(UserProfileEntity::toDomain).toList();
    }

    /** 참여자 중 본인인증 행이 있는 유저 id(#513). user_id 칸만 읽는다(CI/DI 는 매핑하지 않는다). */
    public List<Long> findIdentityVerifiedUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "select i.userId from AdminUserIdentityView i where i.userId in :userIds", Long.class)
                .setParameter("userIds", userIds)
                .getResultList();
    }

    private void appendFilters(StringBuilder jpql, RoomStatus status, LocalDate scheduledDate, Boolean isTest) {
        if (isTest != null) {
            jpql.append(" and r.isTest = :isTest");
        }
        if (status != null) {
            jpql.append(" and r.status = :status");
        }
        if (scheduledDate != null) {
            jpql.append(" and r.scheduledDate = :scheduledDate");
        }
    }

    private void bindFilters(TypedQuery<?> query, RoomStatus status, LocalDate scheduledDate, Boolean isTest) {
        if (isTest != null) {
            query.setParameter("isTest", isTest);
        }
        if (status != null) {
            query.setParameter("status", status);
        }
        if (scheduledDate != null) {
            query.setParameter("scheduledDate", scheduledDate);
        }
    }
}
