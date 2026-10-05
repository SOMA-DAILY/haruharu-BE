package org.son.haruharu.config;

import io.lettuce.core.ClientOptions;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.infrastructure.stomp.RedisFanoutSubscriptionStarter;
import org.son.haruharu.infrastructure.stomp.RedisStompSubscribeListener;
import org.son.haruharu.infrastructure.stomp.StompFanoutRuntime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * STOMP 멀티 인스턴스 팬아웃용 Redis Pub/Sub 설정(#453).
 * <p>
 * 모든 앱 인스턴스가 채널 하나({@code haruharu.chat.fanout.redis.channel})를 구독한다. 발행 인스턴스는 자기 세션에 직접
 * 전달하고 채널로 퍼뜨리며, 다른 인스턴스는 받아서 각자 로컬 simpleBroker 로 자기 세션에 전달한다.
 * <p>
 * enabled=false 면 이 설정 전체가 빠진다. 연결 팩토리·템플릿은 Boot 자동설정이 만들지만 첫 사용 전까지 연결하지 않으므로
 * Redis 없는 로컬·테스트에서도 기동에 영향이 없다.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "haruharu.chat.fanout.redis", name = "enabled", havingValue = "true")
public class RedisConfig {

    // 발행·수신 대기열 상한. 넘치면 버린다 — Pub/Sub 은 원래 at-most-once 이고 클라이언트 재조회로 보완한다.
    private static final int FANOUT_QUEUE_CAPACITY = 10_000;

    @Bean
    public ChannelTopic chatFanoutTopic(@Value("${haruharu.chat.fanout.redis.channel}") String channel) {
        return new ChannelTopic(channel);
    }

    // 발행 및 수신을 각각 단일 스레드로 처리해 순서를 유지한다.
    // 실행기는 이 객체로 감싸 Executor 타입 빈으로 노출하지 않는다 — 노출하면 Boot 가 applicationTaskExecutor 를 만들지 않는다.
    @Bean
    public StompFanoutRuntime stompFanoutRuntime() {
        return new StompFanoutRuntime(singleThreadExecutor("stomp-fanout-publish-"),
                singleThreadExecutor("stomp-fanout-dispatch-"));
    }

    // 구독 전용 연결 관리. 기동 시 자동 시작하지 않는다 — 첫 연결 실패가 실행 전체를 실패시키기에
    // RedisFanoutSubscriptionStarter 가 백그라운드에서 재시도하며 시작한다. 이후 끊김은 컨테이너가 재연결하고 재구독한다.
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                        RedisStompSubscribeListener stompSubscribeListener,
                                                                        ChannelTopic chatFanoutTopic,
                                                                        StompFanoutRuntime stompFanoutRuntime) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(stompFanoutRuntime.dispatchExecutor());
        container.setAutoStartup(false);
        container.addMessageListener(stompSubscribeListener, chatFanoutTopic);
        return container;
    }

    @Bean
    public RedisFanoutSubscriptionStarter redisFanoutSubscriptionStarter(RedisMessageListenerContainer container) {
        return new RedisFanoutSubscriptionStarter(container);
    }

    // 연결이 끊겼다고 인지된 동안 publish 가 재연결을 기다리지 않고 즉시 실패한다
    // 응답 없는 상태는 인지되지 않으므로 timeout(2s)이 막는다
    @Bean
    public LettuceClientOptionsBuilderCustomizer rejectCommandsWhenDisconnected() {
        return builder -> builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }

    // 데몬 스레드라 종료 시 JVM 을 붙잡지 않는다. 대기열이 차면 로그를 남기고 버린다.
    private static ThreadPoolTaskExecutor singleThreadExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(FANOUT_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("STOMP fanout 대기열 초과로 버림: executor={}", threadNamePrefix));
        executor.initialize();
        return executor;
    }
}
