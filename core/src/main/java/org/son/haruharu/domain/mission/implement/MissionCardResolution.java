package org.son.haruharu.domain.mission.implement;

/**
 * 개인별 미션 카드의 해소 상태. 해소는 시간창 안의 기대 채널 활동 또는 시간창 마감으로만 일어난다(#402).
 * 사용자가 카드를 "닫는" 것은 FE 로컬 동작이며 서버 상태를 바꾸지 않는다 — 카드는 활동·마감 전까지 계속 노출된다.
 */
public enum MissionCardResolution {
    VIDEO_UPLOAD_AFTER_VIEW,
    CHAT_MESSAGE_AFTER_VIEW,
    /** 시간창이 닫힐 때까지 기대 채널 활동이 없었던 카드. outcome 확정 배치가 부여한다. */
    EXPIRED;

    public static MissionCardResolution activity(ActivationChannel channel) {
        return switch (channel) {
            case VIDEO -> VIDEO_UPLOAD_AFTER_VIEW;
            case GROUP_CHAT -> CHAT_MESSAGE_AFTER_VIEW;
        };
    }
}
