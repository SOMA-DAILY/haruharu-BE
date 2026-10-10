package org.son.haruharu.domain.mission.types.recording.implement;

import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;

/**
 * 참가자에게 촬영을 요청하는 미션의 주제 문구. 앱은 미션 유형으로 촬영 미션인지 판단한다.
 * 올린 영상은 기존 영상 업로드로 저장하며 이 미션과 연결하지 않는다.
 */
public record RecordingMissionDefinition(String content) implements MissionDefinition {
    public RecordingMissionDefinition {
        content = content == null || content.isBlank() ? null : content;
    }

    @Override
    public MissionType type() { return MissionType.RECORDING; }

    public void validate() {
        if (content == null) throw new InvalidMissionException("details.content", "촬영 주제를 입력해 주세요");
        if (content.codePointCount(0, content.length()) > 500)
            throw new InvalidMissionException("details.content", "500자까지 쓸 수 있어요");
    }
}
