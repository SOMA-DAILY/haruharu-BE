package org.son.haruharu.domain.report.implement;

/**
 * 신고 사유(#100). 피그마 6-5 유저 신고하기 화면의 확정 카피와 1:1 대응한다.
 * 유저 노출 카피는 클라이언트 소유 — 백엔드는 enum 코드만 관리한다.
 */
public enum ReportReason {
    /** 미성년자·아동 안전 */
    CHILD_SAFETY,
    /** 부적절한 사진·영상 */
    INAPPROPRIATE_MEDIA,
    /** 욕설·비방·혐오 표현 */
    ABUSIVE_LANGUAGE,
    /** 허위 프로필·사칭 */
    FAKE_PROFILE,
    /** 광고·스팸 */
    SPAM_AD,
    /** 기타 */
    OTHER
}
