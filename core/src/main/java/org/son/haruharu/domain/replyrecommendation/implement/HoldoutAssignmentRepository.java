package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 추천 효과 비교 그룹 배정(이슈 #560): ai_reply.holdout_assignment. */
public interface HoldoutAssignmentRepository {

    /** 561 표·칸이 있는지. 없으면 배정·그룹 기록을 하지 않는다. */
    boolean schemaReady();

    /** 이 방에서 이 사용자의 배정. 없으면 빈 값. */
    Optional<ExperimentArm> findArm(Long roomId, Long userId);

    /** 이 방에 배정이 하나라도 있는지(첫 배정이 끝났는지). */
    boolean roomAssigned(Long roomId);

    /** 이 방의 첫 배정을 한 요청만 하도록 트랜잭션 끝까지 잠근다(같은 방 동시 첫 요청). */
    void lockRoom(Long roomId);

    /** 넣는다. 이미 있는 행은 그대로 둔다(ON CONFLICT DO NOTHING). */
    void insertAll(List<HoldoutAssignment> assignments, Instant now);
}
