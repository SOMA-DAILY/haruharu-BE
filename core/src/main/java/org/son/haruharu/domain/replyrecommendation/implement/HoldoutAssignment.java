package org.son.haruharu.domain.replyrecommendation.implement;

/**
 * ai_reply.holdout_assignment 한 행. 한 번 넣으면 바꾸지 않는다.
 *
 * @param groupKey 기수 단위 묶음 키: {@code cohort:<id>}, 기수 없는 방이면 {@code room:<id>}
 * @param hash     sha256(salt:groupKey:userId) 앞 8바이트를 2^64 로 나눈 값, [0, 1)
 * @param ratio    배정 때 쓴 HOLDOUT 비율
 */
public record HoldoutAssignment(String groupKey, Long roomId, Long userId, ExperimentArm arm, double hash,
                                double ratio) {}
