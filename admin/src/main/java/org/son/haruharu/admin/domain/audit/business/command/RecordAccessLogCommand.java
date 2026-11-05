package org.son.haruharu.admin.domain.audit.business.command;

import org.son.haruharu.admin.domain.audit.implement.AdminAuditAction;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditTargetType;

/**
 * 인터셉터가 요청 1건을 기록할 때 넘기는 값. 본문은 없다.
 *
 * @param adminId    인증된 관리자. 로그인 요청처럼 아직 없으면 null
 * @param loginEmail 로그인 요청의 입력 이메일. 성공이면 주체 식별에, 실패면 detail 에 쓴다
 */
public record RecordAccessLogCommand(
        Long adminId,
        String loginEmail,
        String ip,
        String method,
        String path,
        AdminAuditAction action,
        AdminAuditTargetType targetType,
        Long targetId,
        int statusCode,
        String userAgent
) {
}
