package org.son.haruharu.domain.mission.implement;

public enum MissionCardResolution {
    DISMISSED,
    VIDEO_UPLOAD_AFTER_VIEW,
    CHAT_MESSAGE_AFTER_VIEW;

    public static MissionCardResolution activity(ActivationChannel channel) {
        return switch (channel) {
            case VIDEO -> VIDEO_UPLOAD_AFTER_VIEW;
            case GROUP_CHAT -> CHAT_MESSAGE_AFTER_VIEW;
        };
    }
}
