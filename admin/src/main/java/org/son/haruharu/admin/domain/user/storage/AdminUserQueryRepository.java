package org.son.haruharu.admin.domain.user.storage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.admin.domain.user.implement.UserParticipation;
import org.son.haruharu.admin.domain.user.implement.UserWithProfile;
import org.son.haruharu.domain.ticket.implement.TicketWallet;
import org.son.haruharu.domain.ticket.storage.TicketWalletEntity;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.storage.IapPurchaseEntity;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.FinalSelectionEntity;
import org.son.haruharu.domain.room.storage.MatchResultEntity;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 어드민 유저 조회. admin 전용 JPQL over core 엔티티(user/profile/ticket/payment/room).
 * 목록은 user↔profile ad-hoc left join(닉네임 표시·검색). 소프트삭제는 Hibernate가 자동 필터.
 */
@Repository
@RequiredArgsConstructor
public class AdminUserQueryRepository {

    private final EntityManager entityManager;

    public List<UserWithProfile> findUsers(UserStatus status, String keyword, int offset, int limit) {
        StringBuilder jpql = new StringBuilder(
                "select u, p from UserEntity u left join UserProfileEntity p on p.userId = u.id where 1 = 1");
        appendFilters(jpql, status, keyword);
        jpql.append(" order by u.id desc");
        TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class);
        bindFilters(query, status, keyword);
        return query.setFirstResult(offset).setMaxResults(limit).getResultList().stream()
                .map(row -> new UserWithProfile(
                        ((UserEntity) row[0]).toDomain(),
                        row[1] == null ? null : ((UserProfileEntity) row[1]).toDomain()))
                .toList();
    }

    public long countUsers(UserStatus status, String keyword) {
        StringBuilder jpql = new StringBuilder(
                "select count(u) from UserEntity u left join UserProfileEntity p on p.userId = u.id where 1 = 1");
        appendFilters(jpql, status, keyword);
        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        bindFilters(query, status, keyword);
        return query.getSingleResult();
    }

    public Optional<User> findUser(Long userId) {
        return entityManager.createQuery("select u from UserEntity u where u.id = :userId", UserEntity.class)
                .setParameter("userId", userId)
                .getResultList().stream().findFirst().map(UserEntity::toDomain);
    }

    public Optional<UserProfile> findProfile(Long userId) {
        return entityManager.createQuery(
                        "select p from UserProfileEntity p where p.userId = :userId", UserProfileEntity.class)
                .setParameter("userId", userId)
                .getResultList().stream().findFirst().map(UserProfileEntity::toDomain);
    }

    public Optional<TicketWallet> findWallet(Long userId) {
        return entityManager.createQuery(
                        "select w from TicketWalletEntity w where w.userId = :userId", TicketWalletEntity.class)
                .setParameter("userId", userId)
                .getResultList().stream().findFirst().map(TicketWalletEntity::toDomain);
    }

    public List<IapPurchase> findPurchases(Long userId) {
        return entityManager.createQuery(
                        "select ip from IapPurchaseEntity ip where ip.userId = :userId order by ip.purchasedAt desc",
                        IapPurchaseEntity.class)
                .setParameter("userId", userId)
                .getResultList().stream().map(IapPurchaseEntity::toDomain).toList();
    }

    public List<UserParticipation> findParticipations(Long userId) {
        return entityManager.createQuery(
                        "select m, r from RoomMemberEntity m join RoomEntity r on r.id = m.roomId "
                                + "where m.userId = :userId order by m.id desc", Object[].class)
                .setParameter("userId", userId)
                .getResultList().stream()
                .map(row -> new UserParticipation(
                        ((RoomEntity) row[1]).toDomain(),
                        ((RoomMemberEntity) row[0]).toDomain().joinedAt()))
                .toList();
    }

    /** 유저의 방 멤버 행과 그 방(최신 입장순). 방 참여 이력(#508)의 기준 목록. */
    public List<Map.Entry<RoomMember, Room>> findMemberships(Long userId) {
        return entityManager.createQuery(
                        "select m, r from RoomMemberEntity m join RoomEntity r on r.id = m.roomId "
                                + "where m.userId = :userId order by m.id desc", Object[].class)
                .setParameter("userId", userId)
                .getResultList().stream()
                .map(row -> Map.entry(((RoomMemberEntity) row[0]).toDomain(), ((RoomEntity) row[1]).toDomain()))
                .toList();
    }

    public List<RoomMember> findRoomMembers(Collection<Long> roomIds) {
        if (roomIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "select m from RoomMemberEntity m where m.roomId in :roomIds", RoomMemberEntity.class)
                .setParameter("roomIds", roomIds)
                .getResultList().stream().map(RoomMemberEntity::toDomain).toList();
    }

    public List<FinalSelection> findSelections(Collection<Long> roomMemberIds) {
        if (roomMemberIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "select s from FinalSelectionEntity s where s.roomMemberId in :ids", FinalSelectionEntity.class)
                .setParameter("ids", roomMemberIds)
                .getResultList().stream().map(FinalSelectionEntity::toDomain).toList();
    }

    public List<MatchResult> findMatches(Collection<Long> roomMemberIds) {
        if (roomMemberIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "select mr from MatchResultEntity mr where mr.memberAId in :ids or mr.memberBId in :ids",
                        MatchResultEntity.class)
                .setParameter("ids", roomMemberIds)
                .getResultList().stream().map(MatchResultEntity::toDomain).toList();
    }

    /** 멤버 행별 올린 영상 수(삭제 제외). 방 활동 집계와 같은 기준. */
    public Map<Long, Long> countVideosByMember(Collection<Long> roomMemberIds) {
        if (roomMemberIds.isEmpty()) {
            return Map.of();
        }
        List<?> rows = entityManager.createNativeQuery(
                        "select v.room_member_id, count(*) from video_posts v "
                                + "where v.room_member_id in (:ids) and v.deleted_at is null group by v.room_member_id")
                .setParameter("ids", roomMemberIds)
                .getResultList();
        return toCountMap(rows);
    }

    /** 방별 이 유저가 그룹 채팅에 보낸 메시지 수(시스템 메시지·1:1 채팅 제외). 방 활동 집계와 같은 기준. */
    public Map<Long, Long> countGroupChatsByRoom(Long userId, Collection<Long> roomIds) {
        if (roomIds.isEmpty()) {
            return Map.of();
        }
        List<?> rows = entityManager.createNativeQuery(
                        "select r.room_id, count(*) from chat_messages c join chat_rooms r on r.id = c.chat_room_id "
                                + "where r.room_id in (:roomIds) and r.type = 'GROUP' and r.deleted_at is null "
                                + "and c.deleted_at is null and c.sender_type = 'USER' and c.sender_id = :userId "
                                + "group by r.room_id")
                .setParameter("roomIds", roomIds)
                .setParameter("userId", userId)
                .getResultList();
        return toCountMap(rows);
    }

    private Map<Long, Long> toCountMap(List<?> rows) {
        return rows.stream().map(row -> (Object[]) row).collect(Collectors.toMap(
                row -> ((Number) row[0]).longValue(), row -> ((Number) row[1]).longValue()));
    }

    private void appendFilters(StringBuilder jpql, UserStatus status, String keyword) {
        if (status != null) {
            jpql.append(" and u.status = :status");
        }
        if (hasKeyword(keyword)) {
            jpql.append(" and lower(p.nickname) like lower(concat('%', :keyword, '%'))");
        }
    }

    private void bindFilters(TypedQuery<?> query, UserStatus status, String keyword) {
        if (status != null) {
            query.setParameter("status", status);
        }
        if (hasKeyword(keyword)) {
            query.setParameter("keyword", keyword.trim());
        }
    }

    private boolean hasKeyword(String keyword) {
        return keyword != null && !keyword.isBlank();
    }
}
