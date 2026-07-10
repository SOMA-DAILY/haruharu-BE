package org.son.haruharu.admin.domain.account.implement;

/** 어드민 staff 역할(RBAC). 최소권한 원칙. */
public enum AdminRole {
    /** 최고 권한(환불·계정관리 등 민감 조작 포함) */
    SUPER,
    /** 운영자(세션·유저·재화 운영) */
    OPERATOR,
    /** CS(조회 위주 고객 대응) */
    CS
}
