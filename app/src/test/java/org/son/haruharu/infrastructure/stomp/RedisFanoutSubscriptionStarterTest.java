package org.son.haruharu.infrastructure.stomp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisFanoutSubscriptionStarterTest {

    @Mock RedisMessageListenerContainer container;
    RedisFanoutSubscriptionStarter starter;

    @AfterEach
    void tearDown() {
        if (starter != null) {
            starter.stop();
        }
    }

    @Test
    @DisplayName("구독 시작이 실패하면 컨테이너를 되돌린(stop) 뒤 성공할 때까지 재시도한다")
    void givenStartFailsTwice_whenStart_thenStopsBeforeEachRetryUntilSuccess() {
        // given
        willThrow(new RedisConnectionFailureException("down"))
                .willThrow(new RedisConnectionFailureException("down"))
                .willAnswer(invocation -> null)
                .given(container).start();
        starter = new RedisFanoutSubscriptionStarter(container, 10, 40);

        // when
        starter.start();

        // then: 실패 2회 → 각 실패 뒤 stop → 3번째에 성공, 이후 더 시도하지 않는다
        verify(container, timeout(2_000).times(3)).start();
        var order = inOrder(container);
        order.verify(container).start();
        order.verify(container).stop();
        order.verify(container).start();
        order.verify(container).stop();
        order.verify(container).start();
        verify(container, after(200).times(3)).start();
    }

    @Test
    @DisplayName("재시도 간격은 두 배씩 늘어나고 상한에서 멈춘다")
    void givenRepeatedFailures_whenRetrying_thenBackoffDoublesUpToMax() {
        // given: 시도 시각을 기록한다
        List<Long> attempts = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger calls = new AtomicInteger();
        willAnswer(invocation -> {
            attempts.add(System.nanoTime());
            if (calls.incrementAndGet() <= 4) {
                throw new RedisConnectionFailureException("down");
            }
            return null;
        }).given(container).start();
        starter = new RedisFanoutSubscriptionStarter(container, 50, 150);

        // when
        starter.start();

        // then: 간격 ≥ 50, 100, 150(상한), 150(상한)
        verify(container, timeout(3_000).times(5)).start();
        long[] minGapsMs = {50, 100, 150, 150};
        for (int i = 0; i < minGapsMs.length; i++) {
            long gapMs = (attempts.get(i + 1) - attempts.get(i)) / 1_000_000;
            assertThat(gapMs).as("재시도 %d 간격", i + 1).isGreaterThanOrEqualTo(minGapsMs[i]);
        }
    }

    @Test
    @DisplayName("재시도 대기 중에 stop 하면 더 이상 시도하지 않는다(종료 시 스레드 정리)")
    void givenWaitingForRetry_whenStop_thenNoFurtherAttempts() {
        // given: 계속 실패하고 대기가 긴 상황
        willThrow(new RedisConnectionFailureException("down")).given(container).start();
        starter = new RedisFanoutSubscriptionStarter(container, 10_000, 10_000);
        starter.start();
        verify(container, timeout(2_000)).start();

        // when
        starter.stop();

        // then
        assertThat(starter.isRunning()).isFalse();
        verify(container, after(300).times(1)).start();
        verify(container, atLeastOnce()).stop();
    }

    @Test
    @DisplayName("start 를 두 번 호출해도 구독 스레드는 하나만 돈다")
    void givenAlreadyStarted_whenStartAgain_thenIgnored() {
        // given
        starter = new RedisFanoutSubscriptionStarter(container, 10, 40);

        // when
        starter.start();
        starter.start();

        // then
        verify(container, after(300).times(1)).start();
        assertThat(starter.isRunning()).isTrue();
    }
}
