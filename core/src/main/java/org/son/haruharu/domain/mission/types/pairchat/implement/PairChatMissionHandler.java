package org.son.haruharu.domain.mission.types.pairchat.implement;

import java.util.Map;
import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.MissionTypeHandler;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.springframework.stereotype.Component;

@Component
public class PairChatMissionHandler implements MissionTypeHandler {

    @Override
    public MissionType type() { return MissionType.PAIR_CHAT; }

    @Override
    public void validate(MissionDefinition definition) { pairChat(definition); }

    /** 대화는 채팅으로 이루어지며 미션 제출을 받지 않는다. */
    @Override
    public boolean requiresResponse() { return false; }

    /** 유형별 입력이 없어 빈 객체로 저장한다. */
    @Override
    public Map<String, Object> toStored(MissionDefinition definition) {
        pairChat(definition);
        return Map.of();
    }

    /** 모르는 키는 버린다(저장은 검증을 거친 빈 객체만 들어간다). */
    @Override
    public MissionDefinition fromStored(Map<String, Object> stored) { return new PairChatMissionDefinition(); }

    /** 대화 시간에는 끝이 있어야 한다. 방이 끝날 때까지 열어 두지 않는다. */
    @Override
    public boolean requiresExplicitClose() { return true; }

    /** 한 방의 대화 시간은 겹치지 않는다. 지금 열렸는지·다음이 언제인지가 하나로 정해져야 한다. */
    @Override
    public boolean exclusiveSchedule() { return true; }

    private PairChatMissionDefinition pairChat(MissionDefinition definition) {
        if (!(definition instanceof PairChatMissionDefinition pairChat))
            throw new InvalidMissionException("details", "1:1 대화 미션 내용을 확인해 주세요");
        return pairChat;
    }
}
