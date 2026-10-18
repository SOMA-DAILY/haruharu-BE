package org.son.haruharu.admin.domain.cohort.storage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.storage.CohortApplicationEntity;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 어드민 기수 신청자 표(#515). 신청 한 페이지를 읽고, 이름(PASS)·번호(프로필)·지난 방은 그 페이지 유저로 한 번씩 더 읽는다
 * — 행마다 조회하지 않는다(N+1 없음).
 */
@Repository
@RequiredArgsConstructor
public class AdminCohortApplicationQueryRepository {

    private static final String FROM = " from CohortApplicationEntity a where a.cohortId = :cohortId";

    private final EntityManager entityManager;

    /** 우선순위(못 넣은 사람 먼저) → 편성 대기 순 → 신청 순. */
    public List<CohortApplication> findApplications(Long cohortId, CohortApplicationStatus status,
                                                    ApplicantGender gender, int offset, int limit) {
        TypedQuery<CohortApplicationEntity> query = entityManager.createQuery("select a" + FROM + filters(status, gender)
                + " order by a.priority desc, a.queuedAt asc nulls last, a.appliedAt asc, a.id asc",
                CohortApplicationEntity.class);
        bind(query, cohortId, status, gender);
        return query.setFirstResult(offset).setMaxResults(limit).getResultList().stream()
                .map(CohortApplicationEntity::toDomain).toList();
    }

    public long countApplications(Long cohortId, CohortApplicationStatus status, ApplicantGender gender) {
        TypedQuery<Long> query = entityManager.createQuery("select count(a)" + FROM + filters(status, gender), Long.class);
        bind(query, cohortId, status, gender);
        return query.getSingleResult();
    }

    /** userId → PASS 이름. PASS 전이면 없다. */
    public Map<Long, String> findNames(Collection<Long> userIds) {
        return pairs("select i.userId, i.userName from AdminUserIdentityView i where i.userId in :userIds", userIds);
    }

    /** userId → 신청 때 적은 번호(user_profiles.phone). */
    public Map<Long, String> findPhones(Collection<Long> userIds) {
        return pairs("select p.userId, p.phone from UserProfileEntity p where p.userId in :userIds", userIds);
    }

    /** userId → 들어갔던 방 id(최근 방부터). */
    public Map<Long, List<Long>> findRoomIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return entityManager.createQuery("select m.userId, m.roomId from RoomMemberEntity m "
                        + "where m.userId in :userIds order by m.roomId desc", Object[].class)
                .setParameter("userIds", userIds).getResultList().stream()
                .collect(Collectors.groupingBy(row -> (Long) row[0], LinkedHashMap::new,
                        Collectors.mapping(row -> (Long) row[1], Collectors.toList())));
    }

    private Map<Long, String> pairs(String jpql, Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, String> result = new LinkedHashMap<>();
        entityManager.createQuery(jpql, Object[].class).setParameter("userIds", userIds).getResultList()
                .forEach(row -> result.put((Long) row[0], (String) row[1]));
        return result;
    }

    private String filters(CohortApplicationStatus status, ApplicantGender gender) {
        return (status == null ? "" : " and a.status = :status") + (gender == null ? "" : " and a.declaredGender = :gender");
    }

    private void bind(TypedQuery<?> query, Long cohortId, CohortApplicationStatus status, ApplicantGender gender) {
        query.setParameter("cohortId", cohortId);
        if (status != null) query.setParameter("status", status);
        if (gender != null) query.setParameter("gender", gender);
    }
}
