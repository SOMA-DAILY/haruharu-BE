package org.son.haruharu.domain.mission.chat.implement;

import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;


/** 게시 계획의 공통 시각·문구 검증. 본문 생략 허용 여부는 미션 유형에서 판단한다. */
public final class MissionAnnouncementContent {
    private MissionAnnouncementContent() {}

    public static String text(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static void validateAnnouncement(RoomMission mission, MissionAnnouncementType type,
                                            java.time.LocalDateTime runAt, String content,
                                            String title, String body, boolean allowEmptyContent) {
        if (type == null) throw new InvalidMissionException("announcement_type", "안내 용도를 선택해 주세요");
        if (runAt == null || runAt.isBefore(mission.opensAt()))
            throw new InvalidMissionException("run_at", "안내는 미션 시작 이후로 지정해 주세요");
        if (text(content) == null && !allowEmptyContent)
            throw new InvalidMissionException("content", "안내 내용을 입력해 주세요");
        length(content, 500, "content");
        length(title, 15, "push_title");
        length(body, 30, "push_body");
    }

    private static void length(String value, int max, String field) {
        if (value != null && value.codePointCount(0, value.length()) > max)
            throw new InvalidMissionException(field, max + "자까지 쓸 수 있어요");
    }
}
