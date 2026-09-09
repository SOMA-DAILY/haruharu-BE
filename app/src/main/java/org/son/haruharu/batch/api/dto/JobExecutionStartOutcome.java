package org.son.haruharu.batch.api.dto;

/**
 * 잡 시작 요청의 결과 분류(#389). 오케스트레이터는 이 값으로 "실제로 돌렸는지"를 판단한다.
 * <ul>
 *   <li>{@code STARTED} — 새 JobInstance 로 실행</li>
 *   <li>{@code RESTARTED} — 같은 식별 파라미터의 이전 실행이 미완료(FAILED 등)라 재시작</li>
 *   <li>{@code ALREADY_COMPLETED} — 이미 완료된 버킷. 실행하지 않고 기존 실행을 돌려줌(멱등 재호출)</li>
 * </ul>
 */
public enum JobExecutionStartOutcome {
    STARTED,
    RESTARTED,
    ALREADY_COMPLETED
}
