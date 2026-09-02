package org.son.haruharu.domain.mission.implement;

/**
 * 후보가 부족할 때 어디까지 제약을 풀었는지. 선언 순서가 곧 완화 순서다.
 * <p>
 * 자기개방 상한·개인정보 위험·호감 노출 위험은 <b>어떤 단계에서도 완화하지 않는다.</b>
 * 후보가 없다는 이유로 위험한 미션을 노출할 수는 없기 때문이다. 그때는 SAFE_FALLBACK 을 쓰고
 * 그마저 없으면 장애로 남긴다.
 */
public enum MissionAssignmentRelaxationLevel {
    /** 모든 제약 적용. */
    NONE,
    /** 같은 semanticCluster 연속 금지를 해제. */
    CLUSTER_RELAXED,
    /** 같은 missionGoal·activationChannel 연속 제한을 해제. */
    FORMAT_RELAXED,
    /** 참여 부담 상한을 해제. */
    BURDEN_RELAXED,
    /** 방 안에서 이미 쓴 revision 재사용을 허용. */
    REUSE_RELAXED,
    /** 일반 후보가 전부 고갈되어 안전 fallback 을 사용. */
    SAFE_FALLBACK
}
