package org.son.haruharu.admin.domain.audit.business;

import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminAccountReader;
import org.son.haruharu.admin.domain.audit.business.command.CreateAuditReviewCommand;
import org.son.haruharu.admin.domain.audit.business.command.RecordAccessLogCommand;
import org.son.haruharu.admin.domain.audit.business.command.RegisterExportCommand;
import org.son.haruharu.admin.domain.audit.business.command.SearchAccessLogCommand;
import org.son.haruharu.admin.domain.audit.business.dto.AdminAccessLogResponse;
import org.son.haruharu.admin.domain.audit.business.dto.AdminAuditAdminResponse;
import org.son.haruharu.admin.domain.audit.business.dto.AdminAuditExportResponse;
import org.son.haruharu.admin.domain.audit.business.dto.AdminAuditReviewResponse;
import org.son.haruharu.admin.domain.audit.business.dto.AdminAuditSummaryResponse;
import org.son.haruharu.admin.domain.audit.business.exception.InvalidAuditPeriodException;
import org.son.haruharu.admin.domain.audit.business.exception.InvalidAuditRangeException;
import org.son.haruharu.admin.domain.audit.implement.AdminAccessLog;
import org.son.haruharu.admin.domain.audit.implement.AdminAccessLogReader;
import org.son.haruharu.admin.domain.audit.implement.AdminAccessLogSearch;
import org.son.haruharu.admin.domain.audit.implement.AdminAccessLogWriter;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditAction;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditReview;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditReviewReader;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditReviewWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 어드민 접속기록 유스케이스(#554): 요청 기록, 반출 사유 등록, 목록·요약 조회, 정기 점검 기록.
 * 기록에는 요청·응답 본문을 넣지 않는다 — 경로와 대상 ID 만.
 */
@Service
@Transactional(readOnly = true)
public class AdminAuditService {

    public static final String EXPORT_PATH = "/api/v1/admin/audit/exports";
    /** 하루 이 수 이상 조회한 관리자를 요약에 따로 올린다(대량 조회 점검). */
    public static final long HEAVY_VIEW_THRESHOLD = 200;
    public static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_SUMMARY_DAYS = 30;
    private static final Pattern PERIOD = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final AdminAccessLogWriter adminAccessLogWriter;
    private final AdminAccessLogReader adminAccessLogReader;
    private final AdminAuditReviewWriter adminAuditReviewWriter;
    private final AdminAuditReviewReader adminAuditReviewReader;
    private final AdminAccountReader adminAccountReader;
    private final Clock clock;

    public AdminAuditService(AdminAccessLogWriter adminAccessLogWriter, AdminAccessLogReader adminAccessLogReader,
                             AdminAuditReviewWriter adminAuditReviewWriter, AdminAuditReviewReader adminAuditReviewReader,
                             AdminAccountReader adminAccountReader, Clock clock) {
        this.adminAccessLogWriter = adminAccessLogWriter;
        this.adminAccessLogReader = adminAccessLogReader;
        this.adminAuditReviewWriter = adminAuditReviewWriter;
        this.adminAuditReviewReader = adminAuditReviewReader;
        this.adminAccountReader = adminAccountReader;
        this.clock = clock;
    }

    /**
     * 요청 1건을 기록한다. 인터셉터의 afterCompletion 에서 호출되므로 컨트롤러 트랜잭션과 무관하게
     * 항상 새 트랜잭션으로 커밋한다(실패한 요청도 남아야 한다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(RecordAccessLogCommand command) {
        Long adminId = command.adminId();
        String detail = null;
        if (command.action() == AdminAuditAction.LOGIN && command.loginEmail() != null) {
            adminId = adminAccountReader.findByEmail(command.loginEmail()).map(AdminAccount::id).orElse(null);
        } else if (command.action() == AdminAuditAction.LOGIN_FAIL && command.loginEmail() != null) {
            detail = "email=" + command.loginEmail();
        }
        adminAccessLogWriter.save(AdminAccessLog.of(adminId, LocalDateTime.now(clock), command.ip(),
                command.method(), command.path(), command.action(), command.targetType(), command.targetId(),
                command.statusCode(), null, detail, command.userAgent()));
    }

    /** 반출 사유를 EXPORT 행으로 남긴다. 사유 길이 검증은 요청 DTO 가 한다. */
    @Transactional
    public AdminAuditExportResponse registerExport(RegisterExportCommand command) {
        AdminAccessLog saved = adminAccessLogWriter.save(AdminAccessLog.of(command.adminId(), LocalDateTime.now(clock),
                command.ip(), "POST", EXPORT_PATH, AdminAuditAction.EXPORT, command.targetType(), command.targetId(),
                200, command.reason(), command.scope(), command.userAgent()));
        return new AdminAuditExportResponse(saved.id());
    }

    public PageResponse<AdminAccessLogResponse> getLogs(SearchAccessLogCommand command) {
        validateRange(command.from(), command.to());
        int size = Math.max(1, Math.min(command.size(), MAX_PAGE_SIZE));
        int page = Math.max(0, command.page());
        AdminAccessLogSearch search = new AdminAccessLogSearch(command.adminId(), command.action(), command.targetType(),
                command.targetId(), startOf(command.from()), endExclusiveOf(command.to()));
        Page<AdminAccessLog> result = adminAccessLogReader.search(search,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        Map<Long, String> names = adminNames();
        List<AdminAccessLogResponse> content = result.getContent().stream()
                .map(log -> AdminAccessLogResponse.from(log, names.get(log.adminId())))
                .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    /** from/to 가 없으면 최근 30일. */
    public AdminAuditSummaryResponse getSummary(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_SUMMARY_DAYS - 1L);
        validateRange(effectiveFrom, effectiveTo);
        LocalDateTime start = startOf(effectiveFrom);
        LocalDateTime end = endExclusiveOf(effectiveTo);
        Map<Long, String> names = adminNames();
        List<AdminAuditSummaryResponse.PerAdmin> perAdmin = adminAccessLogReader.countByAdmin(start, end).stream()
                .map(c -> new AdminAuditSummaryResponse.PerAdmin(admin(c.adminId(), names), c.views(), c.exports()))
                .toList();
        List<AdminAuditSummaryResponse.HeavyViewer> heavy = adminAccessLogReader
                .findHeavyViewDays(start, end, HEAVY_VIEW_THRESHOLD).stream()
                .map(d -> new AdminAuditSummaryResponse.HeavyViewer(admin(d.adminId(), names), d.date(), d.count()))
                .toList();
        return new AdminAuditSummaryResponse(effectiveFrom, effectiveTo, HEAVY_VIEW_THRESHOLD, perAdmin, heavy);
    }

    @Transactional
    public AdminAuditReviewResponse createReview(CreateAuditReviewCommand command) {
        if (command.period() == null || !PERIOD.matcher(command.period()).matches()) {
            throw new InvalidAuditPeriodException();
        }
        AdminAuditReview saved = adminAuditReviewWriter.save(
                AdminAuditReview.create(command.period(), command.adminId(), command.note(), LocalDateTime.now(clock)));
        return AdminAuditReviewResponse.from(saved, adminNames().get(saved.adminId()));
    }

    public List<AdminAuditReviewResponse> getReviews() {
        Map<Long, String> names = adminNames();
        return adminAuditReviewReader.findAll().stream()
                .map(review -> AdminAuditReviewResponse.from(review, names.get(review.adminId())))
                .toList();
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidAuditRangeException();
        }
    }

    private static LocalDateTime startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private static LocalDateTime endExclusiveOf(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }

    /** 어드민 계정은 몇 명 안 되니 한 번에 읽어 이름을 붙인다. */
    private Map<Long, String> adminNames() {
        return adminAccountReader.findAll().stream()
                .collect(Collectors.toMap(AdminAccount::id, AdminAccount::name, (a, b) -> a));
    }

    private static AdminAuditAdminResponse admin(Long adminId, Map<Long, String> names) {
        return new AdminAuditAdminResponse(adminId, names.get(adminId));
    }
}
