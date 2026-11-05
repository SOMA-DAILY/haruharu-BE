package org.son.haruharu.admin.domain.audit.implement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/** 접속기록 저장소 포트. 추가와 조회만 있고 수정·삭제는 없다(#554, 위변조 방지). */
public interface AdminAccessLogRepository {

    AdminAccessLog save(AdminAccessLog log);

    Page<AdminAccessLog> search(AdminAccessLogSearch search, Pageable pageable);

    /** [from, to) 구간의 관리자별 조회·반출 수. 주체를 모르는 행(로그인 실패)은 제외. */
    List<AdminAuditCount> countByAdmin(LocalDateTime from, LocalDateTime to);

    /** [from, to) 구간에서 하루 조회 수가 {@code minCount} 이상인 (관리자, 날짜). */
    List<AdminAuditDailyCount> findHeavyViewDays(LocalDateTime from, LocalDateTime to, long minCount);
}
