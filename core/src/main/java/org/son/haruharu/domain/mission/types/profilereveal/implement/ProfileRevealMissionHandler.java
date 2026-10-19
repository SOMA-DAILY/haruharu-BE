package org.son.haruharu.domain.mission.types.profilereveal.implement;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;
import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.MissionTypeHandler;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProfileRevealMissionHandler implements MissionTypeHandler {
    static final String REVEAL_FIELDS = "reveal_fields";

    @Override
    public MissionType type() { return MissionType.PROFILE_REVEAL; }
    @Override
    public void validate(MissionDefinition definition) { reveal(definition).validate(); }
    @Override
    public boolean requiresResponse() { return false; }

    @Override
    public Map<String, Object> toStored(MissionDefinition definition) {
        return Map.of(REVEAL_FIELDS, reveal(definition).revealFields().stream().map(Enum::name).toList());
    }

    /** 모르는 값은 버린다(저장은 검증을 거친 값만 들어간다). 버린 항목은 끝까지 공개되지 않으므로 경고를 남긴다. */
    @Override
    public MissionDefinition fromStored(Map<String, Object> stored) {
        List<?> names = stored.get(REVEAL_FIELDS) instanceof List<?> list ? list : List.of();
        List<MissionRevealField> fields = names.stream()
                .map(name -> name instanceof String value ? MissionRevealField.parse(value) : Optional.<MissionRevealField>empty())
                .flatMap(Optional::stream).toList();
        if (fields.isEmpty() || fields.size() != names.size()) {
            log.warn("공개 미션 details 에 읽지 못한 공개 항목이 있음: stored={} read={}", names.size(), fields.size());
        }
        return new ProfileRevealMissionDefinition(fields);
    }

    /** 공개만 알리는 START 는 채팅 글 없이 PROFILE_REVEAL 푸시만 보낼 수 있다. */
    @Override
    public boolean allowsEmptyAnnouncement(MissionAnnouncementType purpose, MissionDefinition definition) {
        return purpose == MissionAnnouncementType.START;
    }

    private ProfileRevealMissionDefinition reveal(MissionDefinition definition) {
        if (!(definition instanceof ProfileRevealMissionDefinition reveal))
            throw new InvalidMissionException("details", "공개할 프로필 항목을 입력해 주세요");
        return reveal;
    }
}
