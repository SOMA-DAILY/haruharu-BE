package org.son.haruharu.domain.mission.types.profilereveal.implement;

import java.util.List;
import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;

/** 미션 시작 시각에 방 멤버 프로필에서 여는 항목. 채팅 문구는 안내(MissionAnnouncement)가 가진다. */
public record ProfileRevealMissionDefinition(List<MissionRevealField> revealFields) implements MissionDefinition {
    public ProfileRevealMissionDefinition {
        revealFields = revealFields == null ? List.of() : revealFields.stream().distinct().sorted().toList();
    }

    public static ProfileRevealMissionDefinition from(List<String> revealFields) {
        List<MissionRevealField> fields = revealFields == null ? List.of() : revealFields.stream()
                .map(value -> MissionRevealField.parse(value).orElseThrow(() ->
                        new InvalidMissionException("details.reveal_fields", "공개 항목을 다시 골라주세요"))).toList();
        return new ProfileRevealMissionDefinition(fields);
    }

    @Override
    public MissionType type() { return MissionType.PROFILE_REVEAL; }

    public void validate() {
        if (revealFields.isEmpty())
            throw new InvalidMissionException("details.reveal_fields", "공개할 항목을 하나 이상 골라주세요");
    }
}
