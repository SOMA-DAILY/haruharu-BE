package org.son.haruharu.domain.mission.types.announcement.implement;

import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;

/** 안내형만 소유하는 본문. 공통 미션에는 이 필드를 두지 않는다. */
public record AnnouncementMissionDefinition(String content) implements MissionDefinition {
    public AnnouncementMissionDefinition {
        content = content == null || content.isBlank() ? null : content;
    }

    @Override
    public MissionType type() { return MissionType.ANNOUNCEMENT; }

    public void validate() {
        if (content == null) throw new InvalidMissionException("details.content", "안내 문구를 입력해 주세요");
        if (content.codePointCount(0, content.length()) > 500)
            throw new InvalidMissionException("details.content", "500자까지 쓸 수 있어요");
    }
}
