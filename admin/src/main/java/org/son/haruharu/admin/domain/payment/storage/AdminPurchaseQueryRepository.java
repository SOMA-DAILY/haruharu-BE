package org.son.haruharu.admin.domain.payment.storage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapPurchaseStatus;
import org.son.haruharu.domain.payment.storage.IapPurchaseEntity;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 어드민 결제(IAP) 내역 조회. admin 전용 JPQL over core IapPurchaseEntity.
 * status/userId/storeProductId는 선택 필터라 조건을 동적으로 조립한다. 소프트삭제는 Hibernate가 자동 필터.
 */
@Repository
@RequiredArgsConstructor
public class AdminPurchaseQueryRepository {

    private final EntityManager entityManager;

    public List<IapPurchase> findPurchases(IapPurchaseStatus status, Long userId, String storeProductId,
                                           int offset, int limit) {
        StringBuilder jpql = new StringBuilder("select p from IapPurchaseEntity p where 1 = 1");
        appendFilters(jpql, status, userId, storeProductId);
        jpql.append(" order by p.id desc");
        TypedQuery<IapPurchaseEntity> query = entityManager.createQuery(jpql.toString(), IapPurchaseEntity.class);
        bindFilters(query, status, userId, storeProductId);
        return query.setFirstResult(offset).setMaxResults(limit)
                .getResultList().stream().map(IapPurchaseEntity::toDomain).toList();
    }

    public long countPurchases(IapPurchaseStatus status, Long userId, String storeProductId) {
        StringBuilder jpql = new StringBuilder("select count(p) from IapPurchaseEntity p where 1 = 1");
        appendFilters(jpql, status, userId, storeProductId);
        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        bindFilters(query, status, userId, storeProductId);
        return query.getSingleResult();
    }

    public Optional<IapPurchase> findById(Long id) {
        return entityManager.createQuery("select p from IapPurchaseEntity p where p.id = :id", IapPurchaseEntity.class)
                .setParameter("id", id)
                .getResultList().stream().findFirst().map(IapPurchaseEntity::toDomain);
    }

    /** 구매자 프로필(닉네임) 일괄 조회. 목록·상세의 닉네임 enrich용(N+1 방지). */
    public List<UserProfile> findProfilesByUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        "select p from UserProfileEntity p where p.userId in :userIds", UserProfileEntity.class)
                .setParameter("userIds", userIds)
                .getResultList().stream().map(UserProfileEntity::toDomain).toList();
    }

    private void appendFilters(StringBuilder jpql, IapPurchaseStatus status, Long userId, String storeProductId) {
        if (status != null) {
            jpql.append(" and p.status = :status");
        }
        if (userId != null) {
            jpql.append(" and p.userId = :userId");
        }
        if (storeProductId != null) {
            jpql.append(" and p.storeProductId = :storeProductId");
        }
    }

    private void bindFilters(TypedQuery<?> query, IapPurchaseStatus status, Long userId, String storeProductId) {
        if (status != null) {
            query.setParameter("status", status);
        }
        if (userId != null) {
            query.setParameter("userId", userId);
        }
        if (storeProductId != null) {
            query.setParameter("storeProductId", storeProductId);
        }
    }
}
