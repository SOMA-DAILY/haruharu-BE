package org.son.haruharu.domain.mission.business;

/** 슬롯 하나에 배정할 미션과 재현 가능한 결정 메타데이터를 만드는 정책. */
public interface MissionAssigner {

    MissionDecision assignNext(MissionAssignmentContext context);
}
