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
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
