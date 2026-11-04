package org.son.haruharu.admin.domain.report.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.report.implement.Report;
import org.son.haruharu.domain.report.implement.ReportReason;
import org.son.haruharu.domain.report.implement.ReportStatus;
import org.son.haruharu.domain.user.implement.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 어드민 신고 한 건. 목록·상세 공용. 피신고자의 방 id 로 기존 방 활동 API(영상 썸네일·그룹채팅, userId 필터)를 연다. */
public record AdminReportResponse(
        @Schema(description = "신고 id") Long reportId,
        @Schema(description = "접수 시각") LocalDateTime createdAt,
        @Schema(description = "상태") ReportStatus status,
        @Schema(description = "사유") ReportReason reason,
        @Schema(description = "상세(선택)", nullable = true) String detail,
        @Schema(description = "익명 질문 신고면 질문 id", nullable = true) Long questionId,
        @Schema(description = "신고가 난 방. 질문 신고는 질문의 방, 사용자 신고는 두 사람이 함께 있던 최근 방. 모르면 null", nullable = true) Long roomId,
        @Schema(description = "처리한 관리자 id. 접수 상태면 null", nullable = true) Long resolvedByAdminId,
        @Schema(description = "처리 시각. 접수 상태면 null", nullable = true) LocalDateTime resolvedAt,
        @Schema(description = "신고자") Person reporter,
        @Schema(description = "피신고자") Reported reported,
        @Schema(description = "같은 피신고자 신고 건수(이 건 포함). 목록에선 null", nullable = true) Long reportedUserReportCount
) {
    public record Person(Long userId, String nickname) {}

    public record Reported(Long userId, String nickname, UserStatus status,
                           @Schema(description = "참여한 방 id(최신순)") List<Long> roomIds) {}

    public static AdminReportResponse of(Report r, Long roomId, Long resolvedByAdminId, LocalDateTime resolvedAt,
                                         Person reporter, Reported reported, Long count) {
        return new AdminReportResponse(r.id(), r.createdAt(), r.status(), r.reason(), r.detail(), r.questionId(),
                roomId, resolvedByAdminId, resolvedAt, reporter, reported, count);
    }
}
