package org.son.haruharu.admin.domain.report.business;

import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.report.business.dto.AdminReportResponse;
import org.son.haruharu.admin.domain.report.business.exception.AdminReportAlreadyResolvedException;
import org.son.haruharu.admin.domain.report.implement.AdminReportReader;
import org.son.haruharu.admin.domain.report.implement.AdminReportWriter;
import org.son.haruharu.domain.auth.implement.RefreshTokenWriter;
import org.son.haruharu.domain.report.implement.Report;
import org.son.haruharu.domain.report.implement.ReportStatus;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 어드민 신고 처리(#553). 목록·상세 조회, 제재 승인(피신고자 BANNED + 리프레시 토큰 폐기), 기각.
 * 승인·기각은 접수 상태에서 한 번만(조건부 UPDATE) — 이미 처리된 건은 409.
 * 액세스 토큰은 앱 JwtAuthenticationFilter 가 요청마다 users.status 를 봐서 BANNED 면 403 으로 막는다.
 * 리프레시 토큰 폐기는 BaseEntity 전역 soft-delete 라 deleted_at 만 찍히고, 이후 재발급 조회가 그 행을 걸러낸다.
 */
@Service
@Transactional(readOnly = true)
public class AdminReportService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminReportReader reportReader;
    private final AdminReportWriter reportWriter;
    private final UserReader userReader;
    private final UserWriter userWriter;
    private final RefreshTokenWriter refreshTokenWriter;
    private final Clock clock;

    public AdminReportService(AdminReportReader reportReader, AdminReportWriter reportWriter,
                              UserReader userReader, UserWriter userWriter, RefreshTokenWriter refreshTokenWriter,
                              Clock clock) {
        this.reportReader = reportReader;
        this.reportWriter = reportWriter;
        this.userReader = userReader;
        this.userWriter = userWriter;
        this.refreshTokenWriter = refreshTokenWriter;
        this.clock = clock;
    }

    public PageResponse<AdminReportResponse> getReports(ReportStatus status, int page, int size) {
        int p = Math.max(0, Math.min(page, 100_000)); // page*size int 넘침 방지
        int s = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<Report> reports = reportReader.findReports(status, p * s, s);
        return PageResponse.of(toResponses(reports, false), p, s, reportReader.countReports(status));
    }

    public AdminReportResponse getReport(Long reportId) {
        return toResponses(List.of(reportReader.getById(reportId)), true).getFirst();
    }

    /** 제재 승인: 신고 SANCTIONED + 피신고자 BANNED + 리프레시 토큰 전부 폐기. 유저 행은 잠그고 바꾼다(탈퇴·온보딩 저장과 경쟁 방지). */
    @Transactional
    public AdminReportResponse approve(Long reportId, Long adminId) {
        Report report = resolve(reportId, ReportStatus.SANCTIONED, adminId);
        userReader.lockById(report.reportedUserId());
        User reported = userReader.getById(report.reportedUserId());
        if (reported.status() != UserStatus.BANNED && reported.status() != UserStatus.DELETED) {
            userWriter.save(new User(reported.id(), UserStatus.BANNED, reported.createdAt()));
        }
        refreshTokenWriter.revokeAllByUserId(reported.id());
        return getReport(reportId);
    }

    @Transactional
    public AdminReportResponse reject(Long reportId, Long adminId) {
        resolve(reportId, ReportStatus.REJECTED, adminId);
        return getReport(reportId);
    }

    private Report resolve(Long reportId, ReportStatus result, Long adminId) {
        Report report = reportReader.getById(reportId); // 없으면 404
        if (!reportWriter.resolve(reportId, result, adminId, LocalDateTime.now(clock))) {
            throw new AdminReportAlreadyResolvedException();
        }
        return report;
    }

    /**
     * 사용자 신고가 난 방: 두 사람이 함께 있던 방 중 가장 최근(방 id 가 큰) 방. 없으면 null.
     * ponytail: 신고 시점을 안 보고 최근 방을 고른다. 두 사람이 신고 뒤 다른 방에서 또 만나면 틀림 → 그때 reports.room_id 를 앱이 보내게.
     */
    private static Long sharedRoom(List<Long> reporterRooms, List<Long> reportedRooms) {
        return reportedRooms.stream().filter(reporterRooms::contains).max(Long::compare).orElse(null);
    }

    private List<AdminReportResponse> toResponses(List<Report> reports, boolean withCount) {
        List<Long> userIds = reports.stream()
                .flatMap(r -> Stream.of(r.reporterUserId(), r.reportedUserId())).distinct().toList();
        Map<Long, String> nicknames = reportReader.findNicknamesByUserIds(userIds);
        Map<Long, User> users = reportReader.findUsersByIds(userIds);
        // 신고자 방도 같이 읽는다 — 사용자 신고의 "어느 방" 은 두 사람이 함께 있던 방으로 계산한다.
        Map<Long, List<Long>> roomIds = reportReader.findRoomIdsByUserIds(userIds);
        Map<Long, Long> questionRooms = reportReader.findQuestionRoomIds(
                reports.stream().map(Report::questionId).filter(java.util.Objects::nonNull).distinct().toList());
        Map<Long, org.son.haruharu.admin.domain.report.storage.AdminReportQueryRepository.Resolution> resolutions =
                reportReader.findResolutions(reports.stream().map(Report::id).toList());
        return reports.stream().map(r -> {
            User reported = users.get(r.reportedUserId());
            var resolution = resolutions.get(r.id());
            Long roomId = r.questionId() != null ? questionRooms.get(r.questionId())
                    : sharedRoom(roomIds.getOrDefault(r.reporterUserId(), List.of()),
                    roomIds.getOrDefault(r.reportedUserId(), List.of()));
            return AdminReportResponse.of(r, roomId,
                    resolution == null ? null : resolution.adminId(), resolution == null ? null : resolution.at(),
                    new AdminReportResponse.Person(r.reporterUserId(), nicknames.get(r.reporterUserId())),
                    new AdminReportResponse.Reported(r.reportedUserId(), nicknames.get(r.reportedUserId()),
                            reported == null ? null : reported.status(),
                            roomIds.getOrDefault(r.reportedUserId(), List.of())),
                    withCount ? reportReader.countByReportedUserId(r.reportedUserId()) : null);
        }).toList();
    }
}
