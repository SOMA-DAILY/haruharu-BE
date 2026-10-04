package org.son.haruharu.domain.mission;

import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.son.haruharu.domain.chat.business.*;
import org.son.haruharu.domain.chat.implement.*;
import org.son.haruharu.domain.mission.business.*;
import org.son.haruharu.domain.mission.business.command.OperatorMissionCommand;
import org.son.haruharu.domain.mission.implement.*;
import org.son.haruharu.domain.mission.implement.exception.OperatorMissionStateConflictException;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"haruharu.chat.system-user-id=1", "haruharu.mission.operator-poll-ms=10000000"})
@ActiveProfiles("test")
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class OperatorMissionPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }
    static class MutableClock extends Clock {
        AtomicReference<Instant> time = new AtomicReference<>();
        public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return time.get(); }
        void at(LocalDateTime value) { time.set(value.atZone(getZone()).toInstant()); }
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock testClock() {
            var clock = new MutableClock(); clock.at(LocalDateTime.of(2026,10,4,12,0)); return clock;
        }
    }
    @Autowired MutableClock clock;
    @Autowired OperatorMissionService service;
    @Autowired OperatorMissionDeliveryService delivery;
    @Autowired OperatorMissionReader missions;
    @Autowired RoomWriter rooms;
    @Autowired RoomReader roomReader;
    @Autowired RoomMemberWriter members;
    @Autowired ChatService chats;
    @Autowired ChatRoomReader chatRooms;
    @Autowired ChatMessageReader messages;
    @Autowired UserJpaRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    LocalDateTime now = LocalDateTime.of(2026,10,4,12,0);
    Long roomId;
    @BeforeEach void setup() {
        clock.at(now);
        if (!users.existsById(1L)) assertThat(users.save(UserEntity.from(new User(null, UserStatus.ACTIVE,null))).toDomain().id()).isEqualTo(1L);
        var user = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE,null))).toDomain();
        var room = rooms.save(Room.createScheduled(now.minusHours(1), now.plusDays(2), now.plusDays(2).plusHours(1),
                now.plusDays(2).plusHours(2), now.plusDays(3), 3));
        roomId = room.id();
        members.save(RoomMember.of(roomId,user.id(),"MALE",null,now.minusHours(1)));
    }
    OperatorMission create(LocalDateTime at) {
        return service.create(roomId,7L,new OperatorMissionCommand(UUID.randomUUID().toString(),"DAY1_1","지금 있는 곳 보여주기",false,at));
    }
    long cards(Long id) { return jdbc.queryForObject("select count(*) from chat_messages where operator_mission_id=?",Long.class,id); }
    @Test @DisplayName("동시에 같은 예약을 발송해도 카드 하나와 SENT 결과 하나만 커밋한다")
    void givenDueMission_whenDeliverConcurrently_thenCommitOneCard() throws Exception {
        var mission = create(null);
        try (var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=executor.submit(()->delivery.deliver(mission.id()));
            var b=executor.submit(()->delivery.deliver(mission.id()));
            a.get(10,TimeUnit.SECONDS); b.get(10,TimeUnit.SECONDS);
        }
        assertThat(cards(mission.id())).isEqualTo(1);
        var sent=service.history(roomId,null,20).getFirst();
        assertThat(sent.status()).isEqualTo(OperatorMissionStatus.SENT);
        assertThat(messages.findById(sent.messageId())).isPresent();
        assertThatThrownBy(()->service.cancel(roomId,mission.id())).isInstanceOf(OperatorMissionStateConflictException.class);
    }
    @Test @DisplayName("카드 저장 뒤 트랜잭션이 실패하면 카드와 SENT 결과가 함께 롤백된다")
    void givenDeliveryRollback_whenRetry_thenCommitCardAndStatusTogether() {
        var mission=create(null);
        new TransactionTemplate(transactions).executeWithoutResult(status->{delivery.deliver(mission.id());status.setRollbackOnly();});
        assertThat(cards(mission.id())).isZero();
        assertThat(service.history(roomId,null,20).getFirst().status()).isEqualTo(OperatorMissionStatus.SENDING);
        delivery.deliver(mission.id());
        assertThat(cards(mission.id())).isEqualTo(1);
    }
    @Test @DisplayName("수정 또는 취소 잠금을 기다리는 발송은 커밋된 최신 상태를 읽는다")
    void givenCancelLock_whenDeliveryWaits_thenUseCommittedCancellation() throws Exception {
        var mission=create(now.plusMinutes(1)); clock.at(now.plusMinutes(1));
        var locked=new CountDownLatch(1); var release=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var cancel=executor.submit(()->new TransactionTemplate(transactions).executeWithoutResult(status->{
                service.cancel(roomId,mission.id());locked.countDown();awaitRelease(release);
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var send=executor.submit(()->delivery.deliver(mission.id()));
            try { awaitLock("operator_missions"); } finally {release.countDown();}
            cancel.get(10,TimeUnit.SECONDS);send.get(10,TimeUnit.SECONDS);
        }
        assertThat(cards(mission.id())).isZero();
        assertThat(service.history(roomId,null,20).getFirst().status()).isEqualTo(OperatorMissionStatus.CANCELLED);
    }
    @Test @DisplayName("채팅 잠금 대기 중 촬영 기간이 끝나면 카드 저장을 거절한다")
    void givenChatLock_whenRecordingWindowExpires_thenRejectCard() throws Exception {
        chats.ensureGroupRoom(roomId);
        Long chatId=chatRooms.getByRoomId(roomId).id();
        var mission=create(null);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var holder=executor.submit(()->new TransactionTemplate(transactions).executeWithoutResult(status->{
                chatRooms.lockForUpdate(chatId);locked.countDown();awaitRelease(release);
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var send=executor.submit(()->assertThatThrownBy(()->delivery.deliver(mission.id())).isInstanceOf(RuntimeException.class));
            try { awaitLock("chat_rooms"); clock.at(now.plusDays(2)); } finally {release.countDown();}
            holder.get(10,TimeUnit.SECONDS);send.get(10,TimeUnit.SECONDS);
        }
        delivery.fail(mission.id());
        assertThat(cards(mission.id())).isZero();
        assertThat(service.history(roomId,null,20).getFirst().status()).isEqualTo(OperatorMissionStatus.FAILED);
    }
    @Test @DisplayName("발송된 카드 삭제는 재시도해도 삭제 상태가 유지되고 미션 본문과 촬영 정보가 사라진다")
    void givenSentMission_whenDeleteRepeatedly_thenKeepDeletedCard() {
        var mission=create(null);delivery.deliver(mission.id());
        service.delete(roomId,mission.id());delivery.delete(mission.id());delivery.delete(mission.id());
        var sent=service.history(roomId,null,20).getFirst();
        var message=messages.findById(sent.messageId()).orElseThrow();
        assertThat(sent.deleteProcessedAt()).isNotNull();
        assertThat(message.visibleContent()).isEqualTo("삭제된 메시지");
        assertThat(message.visibleMission()).isNull();
    }
    void awaitLock(String table) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(()->assertThat(jdbc.queryForObject(
                "select count(*) from pg_stat_activity where wait_event_type='Lock' and query like ?",Long.class,"%"+table+"%" )).isPositive());
    }
    static void awaitRelease(CountDownLatch release) {
        try {assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
    }
}
