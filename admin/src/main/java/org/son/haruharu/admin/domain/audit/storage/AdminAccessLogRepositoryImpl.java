package org.son.haruharu.admin.domain.audit.storage;

import jakarta.persistence.criteria.Predicate;
import org.son.haruharu.admin.domain.audit.implement.AdminAccessLog;
import org.son.haruharu.admin.domain.audit.implement.AdminAccessLogRepository;
import org.son.haruharu.admin.domain.audit.implement.AdminAccessLogSearch;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditCount;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditDailyCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AdminAccessLogRepositoryImpl implements AdminAccessLogRepository {

    private final AdminAccessLogJpaRepository jpaRepository;

    public AdminAccessLogRepositoryImpl(AdminAccessLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AdminAccessLog save(AdminAccessLog log) {
        return jpaRepository.save(AdminAccessLogEntity.from(log)).toDomain();
    }

    @Override
    public Page<AdminAccessLog> search(AdminAccessLogSearch search, Pageable pageable) {
        return jpaRepository.findAll(toSpecification(search), pageable).map(AdminAccessLogEntity::toDomain);
    }

    @Override
    public List<AdminAuditCount> countByAdmin(LocalDateTime from, LocalDateTime to) {
        return jpaRepository.countByAdmin(from, to).stream()
                .map(row -> new AdminAuditCount(toLong(row[0]), toLong(row[1]), toLong(row[2])))
                .toList();
    }

    @Override
    public List<AdminAuditDailyCount> findHeavyViewDays(LocalDateTime from, LocalDateTime to, long minCount) {
        return jpaRepository.findHeavyViewDays(from, to, minCount).stream()
                .map(row -> new AdminAuditDailyCount(toLong(row[0]), toLocalDate(row[1]), toLong(row[2])))
                .toList();
    }

    /** null 조건은 건너뛴다. 파라미터를 JPQL 에 null 로 바인딩하면 PostgreSQL 이 타입을 못 정하는 문제를 피한다. */
    private static Specification<AdminAccessLogEntity> toSpecification(AdminAccessLogSearch search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search.adminId() != null) {
                predicates.add(cb.equal(root.get("adminId"), search.adminId()));
            }
            if (search.action() != null) {
                predicates.add(cb.equal(root.get("action"), search.action()));
            }
            if (search.targetType() != null) {
                predicates.add(cb.equal(root.get("targetType"), search.targetType()));
            }
            if (search.targetId() != null) {
                predicates.add(cb.equal(root.get("targetId"), search.targetId()));
            }
            if (search.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), search.from()));
            }
            if (search.to() != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), search.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    /** CAST(... AS DATE) 는 드라이버·방언에 따라 java.sql.Date 또는 LocalDate 로 온다. */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }
}
