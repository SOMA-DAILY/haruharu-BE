package org.son.haruharu.domain.mission.types.pairchat.implement;

import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionType;

/**
 * 운영자가 정한 시간 동안 같은 방 참가자끼리 1:1 대화(PAIR 채팅)를 열 수 있게 하는 미션(#540).
 * 유형별 입력은 없고 미션의 시작·끝 시각이 대화 시간이다. 안내 글은 기존 미션 안내(announcement)를 쓴다.
 */
public record PairChatMissionDefinition() implements MissionDefinition {
    @Override
    public MissionType type() { return MissionType.PAIR_CHAT; }
}
