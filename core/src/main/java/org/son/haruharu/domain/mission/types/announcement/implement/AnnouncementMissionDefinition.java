package org.son.haruharu.domain.mission.types.announcement.implement;

import java.util.List;
import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;

/** 안내형만 소유하는 본문과 공개 규칙. 공통 미션에는 이 필드를 두지 않는다. */
public record AnnouncementMissionDefinition(String content, List<MissionRevealField> revealFields) implements MissionDefinition {
    public AnnouncementMissionDefinition {
        content = content == null || content.isBlank() ? null : content;
        revealFields = revealFields == null ? List.of() : revealFields.stream().distinct().sorted().toList();
    }

    public static AnnouncementMissionDefinition from(String content, List<String> revealFields) {
        List<MissionRevealField> fields = revealFields == null ? List.of() : revealFields.stream()
                .map(value -> MissionRevealField.parse(value).orElseThrow(() ->
                        new InvalidMissionException("details.reveal_fields", "공개 항목을 다시 골라주세요"))).toList();
        return new AnnouncementMissionDefinition(content, fields);
    }

    @Override
    public MissionType type() { return MissionType.ANNOUNCEMENT; }

    public void validate() {
        if (content == null && revealFields.isEmpty())
            throw new InvalidMissionException("details.content", "안내 문구나 공개 항목 중 하나는 있어야 해요");
        if (content != null && content.codePointCount(0, content.length()) > 500)
            throw new InvalidMissionException("details.content", "500자까지 쓸 수 있어요");
    }
}
