package org.son.haruharu.domain.report.business;

import java.time.Clock;
import java.time.LocalDateTime;

import org.son.haruharu.domain.report.business.command.CreateReportCommand;
import org.son.haruharu.domain.report.business.dto.ReportResponse;
import org.son.haruharu.domain.report.business.exception.CannotReportSelfException;
import org.son.haruharu.domain.report.business.exception.ReportAlreadyExistsException;
import org.son.haruharu.domain.report.implement.Report;
import org.son.haruharu.domain.report.implement.ReportReader;
import org.son.haruharu.domain.report.implement.ReportWriter;
import org.son.haruharu.domain.user.implement.UserReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 신고 접수(#100). 접수(RECEIVED) 저장까지가 범위 — 상태 전이·알림·자동 조치는 없으며 운영이 수동 대응한다.
 * 중복 신고: 신고자·대상당 1회. 사전 exists 검사(깨끗한 409)에 더해 DB 유니크 제약이
 * 다중 인스턴스 동시 요청의 이중 접수를 차단한다(check-then-insert만으론 부족 — PR #171 원칙).
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ReportReader reportReader;
    private final ReportWriter reportWriter;
    private final UserReader userReader;
    private final Clock clock;

    public ReportService(ReportReader reportReader, ReportWriter reportWriter,
                         UserReader userReader, Clock clock) {
        this.reportReader = reportReader;
        this.reportWriter = reportWriter;
        this.userReader = userReader;
        this.clock = clock;
    }

    @Transactional
    public ReportResponse report(Long reporterUserId, CreateReportCommand command) {
        if (reporterUserId.equals(command.reportedUserId())) {
            throw new CannotReportSelfException();
        }
        userReader.getById(command.reportedUserId()); // 대상 존재 검증(없으면 404)
        if (reportReader.hasReported(reporterUserId, command.reportedUserId())) {
            throw new ReportAlreadyExistsException();
        }
        Report saved = reportWriter.save(Report.received(
                reporterUserId, command.reportedUserId(), command.reason(), command.detail(),
                LocalDateTime.now(clock)));
        return ReportResponse.from(saved);
    }
}
