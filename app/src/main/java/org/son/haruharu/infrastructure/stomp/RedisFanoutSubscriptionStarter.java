package org.son.haruharu.infrastructure.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 팬아웃 구독을 기동을 막지 않고 백그라운드에서 시작한다(#453).
 * <p>
 * 컨테이너를 기동 lifecycle 에서 바로 시작하면 첫 연결 실패가 컨텍스트 기동을 실패시킨다 — Redis 장애 중 배포·태스크 교체가
 * API 전체 장애로 번진다. 여기서는 별도 스레드에서 성공할 때까지 재시도한다. 한 번 구독되면 이후 끊김은 컨테이너가 복구한다.
 * 구독 전에도 자기 인스턴스 세션은 로컬 전달로 받으므로, 놓치는 것은 다른 인스턴스발 실시간뿐이다.
 * <p>
 * 컨테이너는 start() 실패 후에도 started 플래그가 남아 재호출이 무시되므로, 재시도 전에 stop() 으로 되돌린다.
 */
@Slf4j
public class RedisFanoutSubscriptionStarter implements SmartLifecycle {

    private static final long INITIAL_BACKOFF_MS = 1_000;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final RedisMessageListenerContainer container;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private volatile boolean running;
    private volatile Thread worker;

    public RedisFanoutSubscriptionStarter(RedisMessageListenerContainer container) {
        this(container, INITIAL_BACKOFF_MS, MAX_BACKOFF_MS);
    }

    RedisFanoutSubscriptionStarter(RedisMessageListenerContainer container, long initialBackoffMs, long maxBackoffMs) {
        this.container = container;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        Thread thread = new Thread(this::subscribeUntilSuccess, "stomp-fanout-subscribe");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    private void subscribeUntilSuccess() {
        long backoff = initialBackoffMs;
        while (running) {
            try {
                container.start();
                log.info("STOMP fanout 구독 시작");
                return;
            } catch (RuntimeException e) {
                stopQuietly();
                log.warn("STOMP fanout 구독 시작 실패 — {}ms 후 재시도: cause={}", backoff, e.toString());
            }
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            backoff = Math.min(backoff * 2, maxBackoffMs);
        }
    }

    private void stopQuietly() {
        try {
            container.stop();
        } catch (RuntimeException ignored) {
            // 다음 재시도에서 다시 시작한다
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
        stopQuietly();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
