package org.son.haruharu.admin.domain.report.implement;

import org.son.haruharu.admin.domain.report.storage.AdminReportQueryRepository;
import org.son.haruharu.admin.domain.report.storage.exception.AdminReportNotFoundException;
import org.son.haruharu.domain.report.implement.Report;
import org.son.haruharu.domain.report.implement.ReportStatus;
import org.son.haruharu.domain.user.implement.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 어드민 신고 읽기 협력자. business 는 storage 를 직접 보지 않는다. */
@Component
public class AdminReportReader {

    private final AdminReportQueryRepository queryRepository;

    public AdminReportReader(AdminReportQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    public List<Report> findReports(ReportStatus status, int offset, int limit) {
        return queryRepository.findReports(status, offset, limit);
    }

    public long countReports(ReportStatus status) {
        return queryRepository.countReports(status);
    }

    public Report getById(Long id) {
        return queryRepository.findById(id).orElseThrow(AdminReportNotFoundException::new);
    }

    public long countByReportedUserId(Long reportedUserId) {
        return queryRepository.countByReportedUserId(reportedUserId);
    }

    public Map<Long, String> findNicknamesByUserIds(List<Long> userIds) {
        return queryRepository.findNicknamesByUserIds(userIds);
    }

    public Map<Long, User> findUsersByIds(List<Long> userIds) {
        return queryRepository.findUsersByIds(userIds);
    }

    public Map<Long, List<Long>> findRoomIdsByUserIds(List<Long> userIds) {
        return queryRepository.findRoomIdsByUserIds(userIds);
    }

    public Map<Long, AdminReportQueryRepository.Resolution> findResolutions(List<Long> reportIds) {
        return queryRepository.findResolutions(reportIds);
    }

    public Map<Long, Long> findQuestionRoomIds(List<Long> questionIds) {
        return queryRepository.findQuestionRoomIds(questionIds);
    }
}
