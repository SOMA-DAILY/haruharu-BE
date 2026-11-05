package org.son.haruharu.admin.domain.report.implement;

import java.time.LocalDateTime;

/** 신고 처리자와 처리 시각(#553). reports 의 resolved_* 칸은 core Report 도메인에 없어 따로 읽는다. */
public record ReportResolution(Long adminId, LocalDateTime at) {}
