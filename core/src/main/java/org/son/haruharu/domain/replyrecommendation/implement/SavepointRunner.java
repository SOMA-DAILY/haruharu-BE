package org.son.haruharu.domain.replyrecommendation.implement;

/**
 * 지금 트랜잭션 안에서 작업을 savepoint 로 감싸 실행한다. 작업이 실패하면 savepoint 까지만 되돌리고 false 를 돌려준다
 * — 바깥 트랜잭션(예: 영상 게시)은 그대로 계속된다. 트랜잭션 밖이면 그냥 실행하고 실패를 삼킨다.
 */
public interface SavepointRunner {

    boolean runIsolated(Runnable work);
}
