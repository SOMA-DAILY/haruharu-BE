package org.son.haruharu.infrastructure.stomp;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.TaskExecutor;

import java.util.UUID;

/**
 * 인스턴스 하나의 팬아웃 실행 자원(#453): 발행 origin 으로 쓰는 인스턴스 id 와 발행·수신 전용 실행기.
 * <p>
 * 실행기를 {@code Executor} 타입 빈으로 노출하지 않으려고 이 객체로 감싼다 — Executor 빈이 하나라도 있으면
 * Boot 가 applicationTaskExecutor 생성을 건너뛴다. 종료 시 두 실행기를 정리한다.
 */
public class StompFanoutRuntime implements DisposableBean {

    private final String instanceId;
    private final TaskExecutor publishExecutor;
    private final TaskExecutor dispatchExecutor;

    public StompFanoutRuntime(TaskExecutor publishExecutor, TaskExecutor dispatchExecutor) {
        this(UUID.randomUUID().toString(), publishExecutor, dispatchExecutor);
    }

    public StompFanoutRuntime(String instanceId, TaskExecutor publishExecutor, TaskExecutor dispatchExecutor) {
        this.instanceId = instanceId;
        this.publishExecutor = publishExecutor;
        this.dispatchExecutor = dispatchExecutor;
    }

    /** 이 인스턴스가 발행한 envelope 의 origin. 수신 시 같으면 이미 로컬 전달한 자기 메시지라 버린다. */
    public String instanceId() {
        return instanceId;
    }

    /** Redis 발행 전용(단일 스레드). 채팅방 잠금 밖에서 네트워크 I/O 를 하고 발행 순서를 유지한다. */
    public TaskExecutor publishExecutor() {
        return publishExecutor;
    }

    /** 구독 메시지 로컬 전달 전용(단일 스레드). 받은 순서대로 전달한다. */
    public TaskExecutor dispatchExecutor() {
        return dispatchExecutor;
    }

    @Override
    public void destroy() throws Exception {
        if (publishExecutor instanceof DisposableBean disposable) {
            disposable.destroy();
        }
        if (dispatchExecutor instanceof DisposableBean disposable) {
            disposable.destroy();
        }
    }
}
