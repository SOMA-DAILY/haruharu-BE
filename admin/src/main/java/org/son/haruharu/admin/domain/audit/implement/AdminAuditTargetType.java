package org.son.haruharu.admin.domain.audit.implement;

/** 접속기록이 가리키는 정보주체/대상의 종류. 경로의 path variable 이름으로 판별한다(#554). */
public enum AdminAuditTargetType {
    USER,
    ROOM,
    REPORT,
    ADMIN,
    COHORT,
    RECRUIT_TRACK,
    IAP_PRODUCT,
    IAP_PURCHASE,
    MID_PARTICIPATION_CAMPAIGN
}
