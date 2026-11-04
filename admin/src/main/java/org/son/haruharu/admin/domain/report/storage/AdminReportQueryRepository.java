package org.son.haruharu.admin.domain.report.storage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.report.implement.Report;
import org.son.haruharu.domain.report.implement.ReportStatus;
import org.son.haruharu.domain.report.storage.ReportEntity;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 어드민 신고 조회·처리(#553). admin 전용 JPQL over core ReportEntity. 닉네임·유저 상태·참여 방은 IN 일괄 조회로 enrich. */
@Repository
@RequiredArgsConstructor
public class AdminReportQueryRepository {

    private final EntityManager entityManager;

    public List<Report> findReports(ReportStatus status, int offset, int limit) {
        String jpql = "select r from ReportEntity r" + (status != null ? " where r.status = :status" : "")
                + " order by r.id desc";
        TypedQuery<ReportEntity> query = entityManager.createQuery(jpql, ReportEntity.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.setFirstResult(offset).setMaxResults(limit)
                .getResultList().stream().map(ReportEntity::toDomain).toList();
    }

    public long countReports(ReportStatus status) {
        String jpql = "select count(r) from ReportEntity r" + (status != null ? " where r.status = :status" : "");
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        return query.getSingleResult();
    }

    public Optional<Report> findById(Long id) {
        return entityManager.createQuery("select r from ReportEntity r where r.id = :id", ReportEntity.class)
                .setParameter("id", id)
                .getResultList().stream().findFirst().map(ReportEntity::toDomain);
    }

    /**
     * 접수 상태인 신고만 조건부 UPDATE 로 처리한다. 영향 행 수가 0 이면 이미 처리됐거나 없는 건 — 호출자가 409/404 를 가른다.
     * 판단과 쓰기를 한 문장으로 합쳐 승인·기각 동시 요청 중 하나만 통과시킨다(PR #171 원칙).
     */
    public int resolveIfReceived(Long id, ReportStatus result, Long adminId, LocalDateTime now) {
        entityManager.flush();
        int updated = entityManager.createQuery(
                        "update ReportEntity r set r.status = :result, r.updatedAt = :now,"
                                + " r.resolvedByAdminId = :adminId, r.resolvedAt = :now"
                                + " where r.id = :id and r.status = :received")
                .setParameter("result", result)
                .setParameter("adminId", adminId)
                .setParameter("now", now)
                .setParameter("id", id)
                .setParameter("received", ReportStatus.RECEIVED)
                .executeUpdate();
        entityManager.clear(); // 벌크 UPDATE 는 영속성 컨텍스트를 거치지 않는다 — 이미 읽은 엔티티가 옛 상태로 남지 않게.
        return updated;
    }

    /** 신고별 처리자·처리 시각. 접수 상태면 둘 다 null 이라 결과에 없다. */
    public Map<Long, Resolution> findResolutions(List<Long> reportIds) {
        if (reportIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery(
                        "select r.id, r.resolvedByAdminId, r.resolvedAt from ReportEntity r"
                                + " where r.id in :ids and r.resolvedAt is not null", Object[].class)
                .setParameter("ids", reportIds)
                .getResultList().stream()
                .collect(Collectors.toMap(row -> (Long) row[0],
                        row -> new Resolution((Long) row[1], (LocalDateTime) row[2])));
    }

    public record Resolution(Long adminId, LocalDateTime at) {}

    /** 질문 id → 질문이 속한 방 id. */
    public Map<Long, Long> findQuestionRoomIds(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery(
                        "select q.id, q.roomId from QuestionEntity q where q.id in :ids", Object[].class)
                .setParameter("ids", questionIds)
                .getResultList().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    /** 같은 피신고자에게 들어온 신고 수(이 건 포함). */
    public long countByReportedUserId(Long reportedUserId) {
        return entityManager.createQuery(
                        "select count(r) from ReportEntity r where r.reportedUserId = :userId", Long.class)
                .setParameter("userId", reportedUserId)
                .getSingleResult();
    }

    public Map<Long, String> findNicknamesByUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery(
                        "select p from UserProfileEntity p where p.userId in :userIds", UserProfileEntity.class)
                .setParameter("userIds", userIds)
                .getResultList().stream().map(UserProfileEntity::toDomain)
                .collect(Collectors.toMap(p -> p.userId(), p -> p.nickname(), (a, b) -> a));
    }

    public Map<Long, User> findUsersByIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery("select u from UserEntity u where u.id in :ids", UserEntity.class)
                .setParameter("ids", userIds)
                .getResultList().stream().map(UserEntity::toDomain)
                .collect(Collectors.toMap(User::id, Function.identity()));
    }

    /** 유저별 참여 방 id(최신순). 어드민 화면이 기존 방 활동 API(영상 썸네일·그룹채팅, userId 필터)를 이 방들로 연다. */
    public Map<Long, List<Long>> findRoomIdsByUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery(
                        "select m.userId, m.roomId from RoomMemberEntity m where m.userId in :ids order by m.id desc",
                        Object[].class)
                .setParameter("ids", userIds)
                .getResultList();
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((Long) row[1]);
        }
        return result;
    }
}
