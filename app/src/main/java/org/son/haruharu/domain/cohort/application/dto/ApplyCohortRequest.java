package org.son.haruharu.domain.cohort.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.business.command.ApplyCohortCommand;

import java.util.Map;

/** 칸 검증은 도메인(ApplicationForm)이 해서 칸별 메시지(VALIDATION_ERROR)로 알려준다. */
public record ApplyCohortRequest(
        @Schema(description = "학교(1~40자). 프로필에도 저장한다", example = "한국대학교") String school,
        @Schema(description = "학과(1~15자). 프로필에도 저장한다", example = "컴퓨터공학과") String department,
        @Schema(description = "휴대전화 번호. 하이픈은 빼고 저장한다", example = "010-1234-5678") String phone,
        @Schema(description = "트랙 추가 문항의 답(문항 key → 답). 선택형은 보기 중 하나",
                example = "{\"grade\": \"3학년\"}") Map<String, String> answers
) {
    public ApplyCohortCommand toCommand() {
        return new ApplyCohortCommand(school, department, phone, answers);
    }
}
