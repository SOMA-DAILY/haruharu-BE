package org.son.haruharu.domain.mission.implement;

public enum MissionCardResolution {
    DISMISSED,
    VIDEO_UPLOAD_AFTER_VIEW,
    CHAT_MESSAGE_AFTER_VIEW,
    /** 시간창이 닫힐 때까지 노출 후 활동도 dismiss 도 없었던 카드. outcome 확정 배치가 부여한다. */
    EXPIRED;

    public static MissionCardResolution activity(ActivationChannel channel) {
        return switch (channel) {
            case VIDEO -> VIDEO_UPLOAD_AFTER_VIEW;
            case GROUP_CHAT -> CHAT_MESSAGE_AFTER_VIEW;
        };
    }
}
