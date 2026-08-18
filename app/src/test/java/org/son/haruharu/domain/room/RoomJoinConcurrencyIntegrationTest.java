package org.son.haruharu.domain.room;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.exception.AlreadyJoinedRoomException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.room.storage.RoomInviteJpaRepository;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 초대코드 동시 입장 경쟁 조건(#321). 클래스 레벨 @Transactional 을 붙이지 않는다 —
 * 각 스레드의 join 이 독립 트랜잭션으로 커밋돼야 유저 행 비관적 락의 직렬화가 실제로 검증된다.
 * (H2 MODE=PostgreSQL 은 SELECT ... FOR UPDATE 를 실동작시킨다.)
 */
@SpringBootTest
@ActiveProfiles("test")
class RoomJoinConcurrencyIntegrationTest {

    @Autowired RoomInviteService roomInviteService;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomMemberReader roomMemberReader;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired RoomMemberJpaRepository roomMemberJpaRepository;
    @Autowired RoomInviteJpaRepository roomInviteJpaRepository;
    @Autowired RoomJpaRepository roomJpaRepository;

    @AfterEach
    void clean() {
        roomMemberJpaRepository.deleteAll();
        roomInviteJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
    }

    private Long newActiveUser() {
        return userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    private Long newJoinableRoom() {
        LocalDateTime starts = LocalDateTime.now().plusHours(1); // now < startsAt → PENDING(입장 가능)
        Room room = roomWriter.save(Room.createScheduled(starts, starts.plusDays(2).withHour(20),
                starts.plusDays(2).withHour(22), starts.plusDays(2).withHour(23),
                starts.plusDays(2).withHour(23).withMinute(30), 3));
        return room.id();
    }

    @Test
    @DisplayName("한 유저가 서로 다른 코드 2개로 동시에 입장하면 하나만 성공하고 활성 멤버십은 정확히 1개다")
    void givenTwoCodes_whenJoinConcurrently_thenOnlyOneSucceeds() throws Exception {
        Long userId = newActiveUser();
        Long roomA = newJoinableRoom();
        Long roomB = newJoinableRoom();
        roomInviteService.issue(roomA, "CODEAA", 6, null, "admin");
        roomInviteService.issue(roomB, "CODEBB", 6, null, "admin");

        AtomicInteger success = new AtomicInteger();
        AtomicInteger alreadyJoined = new AtomicInteger();
        AtomicInteger others = new AtomicInteger();

        runConcurrently(
                () -> attemptJoin(userId, "CODEAA", success, alreadyJoined, others),
                () -> attemptJoin(userId, "CODEBB", success, alreadyJoined, others));

        assertThat(success.get()).isEqualTo(1);
        assertThat(alreadyJoined.get()).isEqualTo(1);
        assertThat(others.get()).isZero();
        // 두 방을 통틀어 이 유저의 room_member 행은 정확히 1개여야 한다(유령 멤버십·정원 낭비 없음).
        long memberships = roomMemberReader.getByRoomId(roomA).stream().filter(m -> m.userId().equals(userId)).count()
                + roomMemberReader.getByRoomId(roomB).stream().filter(m -> m.userId().equals(userId)).count();
        assertThat(memberships).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 유저가 같은 방 코드로 동시에 입장하면 둘 다 성공한다(유저 단위 락이 방을 막지 않는다)")
    void givenTwoUsers_whenJoinSameRoomConcurrently_thenBothSucceed() throws Exception {
        Long userA = newActiveUser();
        Long userB = newActiveUser();
        Long roomId = newJoinableRoom();
        roomInviteService.issue(roomId, "SHARED", 6, null, "admin");

        AtomicInteger success = new AtomicInteger();
        AtomicInteger alreadyJoined = new AtomicInteger();
        AtomicInteger others = new AtomicInteger();

        runConcurrently(
                () -> attemptJoin(userA, "SHARED", success, alreadyJoined, others),
                () -> attemptJoin(userB, "SHARED", success, alreadyJoined, others));

        assertThat(success.get()).isEqualTo(2);
        assertThat(alreadyJoined.get()).isZero();
        assertThat(others.get()).isZero();
        assertThat(roomMemberReader.getByRoomId(roomId)).hasSize(2);
    }

    private void attemptJoin(Long userId, String code, AtomicInteger success,
                             AtomicInteger alreadyJoined, AtomicInteger others) {
        try {
            roomInviteService.join(userId, code);
            success.incrementAndGet();
        } catch (AlreadyJoinedRoomException e) {
            alreadyJoined.incrementAndGet();
        } catch (RuntimeException e) {
            others.incrementAndGet();
        }
    }

    /** 두 작업을 하나의 게이트로 동시에 풀어 실제 경쟁을 재현한다. */
    private void runConcurrently(Runnable a, Runnable b) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        for (Runnable task : new Runnable[]{a, b}) {
            pool.submit(() -> {
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
    }
}
