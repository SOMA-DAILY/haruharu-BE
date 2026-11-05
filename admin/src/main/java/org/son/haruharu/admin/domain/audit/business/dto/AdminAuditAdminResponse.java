package org.son.haruharu.admin.domain.audit.business.dto;

/** 접속기록에 붙이는 관리자 요약. 삭제됐거나 모르는 계정이면 이름이 null. */
public record AdminAuditAdminResponse(Long id, String name) {
}
