package org.son.haruharu.domain.chat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.son.haruharu.domain.chat.storage.exception.ChatRoomNotFoundException;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatRoomLeavePostgresIntegrationTest extends ChatRoomLeaveIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired PlatformTransactionManager transactions;

    @Test
    @DisplayName("나가기 트랜잭션이 잠금을 잡으면 전송은 커밋을 기다린 뒤 거절되며 메시지를 저장하지 않는다")
    void givenLeaveLock_whenConcurrentSend_thenWaitsAndRejects() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch sending = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var leave = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                chatService.leaveRoom(roomId, a);
                locked.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var send = executor.submit(() -> {
                sending.countDown();
                assertThatThrownBy(() -> chatService.sendMessage(roomId, b, "must not save"))
                        .isInstanceOf(ChatRoomNotFoundException.class);
            });
            assertThat(sending.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                // DB가 실제 잠금 대기로 들어갔는지 확인하므로 스레드 시작 여부만 검증하지 않는다.
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like '%chat_rooms%'", Long.class)).isPositive());
            } finally { release.countDown(); }
            leave.get(10, TimeUnit.SECONDS);
            send.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where chat_room_id = ?", Long.class, roomId)).isZero();
    }

    @Test
    @DisplayName("양쪽 사용자가 동시에 나가도 둘 다 성공하며 방은 한 번 종료된다")
    void givenTwoMembers_whenConcurrentLeave_thenBothSucceed() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { start.await(); chatService.leaveRoom(roomId, a); return true; });
            var second = executor.submit(() -> { start.await(); chatService.leaveRoom(roomId, b); return true; });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(rooms.findById(roomId)).isEmpty();
        assertThat(members.findByChatRoomId(roomId)).allMatch(m -> m.toDomain().hasLeft());
    }

    @Autowired org.son.haruharu.domain.chat.business.ChatDeliveryService deliveryService;

    @Test
    @DisplayName("외부 전달이 먼저 시작되면 나가기는 전달 완료까지 대기하고 종료 뒤 대기 전달은 생략한다")
    void givenDeliveryLock_whenLeaveRaces_thenClosesAfterDeliveryAndSkipsLateDelivery() throws Exception {
        CountDownLatch delivering = new CountDownLatch(1);
        CountDownLatch finishDelivery = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger delivered = new java.util.concurrent.atomic.AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var delivery = executor.submit(() -> deliveryService.deliverIfActive(roomId, () -> {
                delivering.countDown();
                try { assertThat(finishDelivery.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                delivered.incrementAndGet();
            }));
            assertThat(delivering.await(10, TimeUnit.SECONDS)).isTrue();
            var leave = executor.submit(() -> chatService.leaveRoom(roomId, a));
            try {
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like '%chat_rooms%'", Long.class)).isPositive());
                assertThat(rooms.findById(roomId)).isPresent();
            } finally { finishDelivery.countDown(); }
            delivery.get(10, TimeUnit.SECONDS);
            leave.get(10, TimeUnit.SECONDS);
        }
        deliveryService.deliverIfActive(roomId, delivered::incrementAndGet);
        assertThat(delivered.get()).isEqualTo(1);
        assertThat(rooms.findById(roomId)).isEmpty();
    }
}
