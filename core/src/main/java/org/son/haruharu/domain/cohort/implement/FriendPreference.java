package org.son.haruharu.domain.cohort.implement;

/** 결제 뒤 설문의 친구 응답. 운영진 참고값이고 친구 연결은 하지 않는다. */
public enum FriendPreference {
    /** 혼자 신청 */
    ALONE,
    /** 친구와 같은 방 희망 */
    SAME_ROOM,
    /** 친구와 다른 방 희망 */
    DIFFERENT_ROOM,
    /** 상관없음 */
    ANY
}
