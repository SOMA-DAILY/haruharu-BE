package org.son.haruharu.domain.chat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.son.haruharu.domain.chat.business.*;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse.MessageItem;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageWriter;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.mission.chat.business.MissionDispatchStep;
import org.son.haruharu.domain.mission.chat.implement.*;
import org.son.haruharu.domain.mission.chat.storage.MissionAnnouncementEntity;
import org.son.haruharu.domain.mission.chat.storage.MissionAnnouncementJpaRepository;
import org.son.haruharu.domain.mission.common.implement.MissionTypeRegistry;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.storage.RoomMissionEntity;
import org.son.haruharu.domain.mission.common.storage.RoomMissionJpaRepository;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.storage.*;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.son.haruharu.domain.video.storage.VideoPostJpaRepository;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** 저장·락은 실제 DB에서 실행한다. spy는 커밋 대기와 저장 진입·DB 세션 관측만 설치한다. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatMessageSyncWritePathPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired ChatService chats;
    @Autowired ChatPhotoService photos;
    @Autowired VideoReplyService videoReplies;
    @Autowired AdminChatMessageService systemMessages;
    @Autowired MissionDispatchStep missionStep;
    @Autowired MissionAnnouncementReader announcements;
    @Autowired MissionAnnouncementJpaRepository announcementRows;
    @Autowired RoomMissionJpaRepository missionRows;
    @Autowired MissionTypeRegistry missionTypes;
    @Autowired UserJpaRepository users;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired VideoPostJpaRepository videos;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean S3StoragePort storage;
    @MockitoSpyBean ChatMessageWriter messageWriter;
    @MockitoSpyBean ChatRoomReader chatRooms;

    private final ThreadLocal<CommitGate> delayedCommit = new ThreadLocal<>();
    private final ThreadLocal<WriteObservation> contender = new ThreadLocal<>();
    private Long roomId, chatId, sender, videoId;
    private MissionAnnouncement announcement;
    private byte[] image;

    enum WritePath { TEXT, PHOTO, VIDEO_REPLY, SYSTEM, MISSION }

    static Stream<Arguments> writePaths() {
        // 각 경로가 먼저 저장하는 경우와 뒤에서 저장하는 경우를 모두 검증한다.
        // TEXT↔TEXT는 기존 ChatMessageSyncPostgresIntegrationTest에서 검증한다.
        return Stream.of(WritePath.PHOTO, WritePath.VIDEO_REPLY, WritePath.SYSTEM, WritePath.MISSION)
                .flatMap(path -> Stream.of(Arguments.of(path, WritePath.TEXT), Arguments.of(WritePath.TEXT, path)));
    }

    @BeforeEach
    void setup() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(25), now.plusHours(23),
                now.plusHours(25), now.plusHours(26), now.plusHours(47), 3))).toDomain().id();
        sender = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        Long author = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        members.save(RoomMemberEntity.from(new RoomMember(null, roomId, sender, "MALE", "테스트하나", null, now)));
        Long authorMember = members.save(RoomMemberEntity.from(
                new RoomMember(null, roomId, author, "FEMALE", "테스트둘", null, now))).toDomain().id();
        chats.createGroupRoom(roomId);
        chatId = chats.getGroupRoomBySourceRoom(roomId, sender).chatRoomId();
        videoId = videos.save(VideoPostEntity.from(VideoPost.create(authorMember, now.minusHours(1), null,
                "테스트 영상", "https://example.com/video.mp4", "https://example.com/thumb.jpg", now))).toDomain().id();

        var definition = new AnnouncementMissionDefinition("경쟁 테스트 안내");
        var mission = missionRows.save(RoomMissionEntity.from(RoomMission.create(roomId, definition.type(), definition,
                now.minusMinutes(1), null, "admin:test"), missionTypes)).toDomain(missionTypes);
        Long announcementId = announcementRows.save(MissionAnnouncementEntity.from(MissionAnnouncement.scheduled(
                mission, MissionAnnouncementType.START, now.minusMinutes(1), "미션 안내", false,
                null, null, "admin:test"))).toDomain().id();
        assertThat(missionStep.claim(announcementId, now)).isTrue();
        announcement = announcements.getById(announcementId);

        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "png", bytes);
        image = bytes.toByteArray();
        when(storage.publicBaseUrl()).thenReturn("https://media.example/");
        when(storage.createPresignedImageGetUrl(anyString())).thenAnswer(call -> call.getArgument(0));

        doAnswer(call -> {
            var observed = contender.get();
            if (observed != null) observed.saveEntered.set(true);
            ChatMessage saved = (ChatMessage) call.callRealMethod();
            CommitGate gate = delayedCommit.get();
            if (gate != null) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                // 등록 위치가 실제 save 안이므로 MissionDispatchStep의 REQUIRES_NEW에도 들어간다.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        gate.saved.complete(new SavedMessage(saved.id(), databaseSession()));
                        awaitRelease(gate.release);
                    }
                });
            }
            return saved;
        }).when(messageWriter).save(any(ChatMessage.class));

        doAnswer(call -> {
            var observed = contender.get();
            if (observed != null) observed.session.complete(databaseSession());
            return call.callRealMethod();
        }).when(chatRooms).lockForUpdate(anyLong());
    }

    @ParameterizedTest(name = "저장 후 커밋 지연={0}, 뒤따르는 저장={1}")
    @MethodSource("writePaths")
    @DisplayName("모든 생성 경로가 작은 ID의 커밋을 앞질러 동기화 상한을 전진시키지 않는다")
    void givenDelayedCommitAcrossWritePaths_whenSync_thenRecoverEveryCommittedMessage(
            WritePath firstPath, WritePath secondPath) throws Exception {
        long before = write(WritePath.TEXT);
        var gate = new CommitGate();
        var secondWrite = new WriteObservation();
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                Future<Long> first = executor.submit(() -> {
                    delayedCommit.set(gate);
                    try { return write(firstPath); }
                    finally { delayedCommit.remove(); }
                });
                await().atMost(Duration.ofSeconds(10)).until(() -> gate.saved.isDone() || first.isDone());
                if (first.isDone()) first.get(10, TimeUnit.SECONDS); // fixture/업무 오류를 timeout으로 숨기지 않는다.
                SavedMessage saved = gate.saved.get(10, TimeUnit.SECONDS);
                assertThat(jdbc.queryForObject("select count(*) from chat_messages where id=?", Long.class, saved.id()))
                        .as("커밋 직전 첫 메시지는 다른 연결에 아직 보이지 않는다").isZero();

                Future<Long> second = executor.submit(() -> {
                    contender.set(secondWrite);
                    try { return write(secondPath); }
                    finally { contender.remove(); }
                });
                await().atMost(Duration.ofSeconds(10)).until(() -> secondWrite.session.isDone() || second.isDone());
                if (second.isDone()) second.get(10, TimeUnit.SECONDS);
                int waitingPid = secondWrite.session.get(10, TimeUnit.SECONDS);
                await().atMost(Duration.ofSeconds(10)).until(() -> second.isDone() || isBlockedBy(waitingPid, saved.pid()));
                if (second.isDone()) second.get(10, TimeUnit.SECONDS);

                var during = chats.syncMessages(chatId, sender, before, null, 100);
                assertThat(during.syncTargetMessageId()).as("미커밋 작은 ID를 건너뛰는 상한 금지").isEqualTo(before);
                assertThat(during.messages()).isEmpty();
                assertThat(isBlockedBy(waitingPid, saved.pid())).as("두 번째 실제 DB 세션은 첫 트랜잭션의 잠금을 기다린다").isTrue();
                assertThat(secondWrite.saveEntered.get()).as("방 잠금을 획득하기 전에 IDENTITY 저장에 진입하지 않는다").isFalse();
                assertThat(second.isDone()).isFalse();

                gate.release.countDown();
                long firstId = first.get(10, TimeUnit.SECONDS);
                long secondId = second.get(10, TimeUnit.SECONDS);
                assertThat(firstId).isEqualTo(saved.id());
                assertThat(secondId).isGreaterThan(firstId);
                var recovered = chats.syncMessages(chatId, sender, during.nextAfterMessageId(), null, 100);
                assertThat(recovered.messages()).extracting(MessageItem::messageId).containsExactly(firstId, secondId);
                assertThat(recovered.nextAfterMessageId()).isEqualTo(secondId);
                assertThat(recovered.hasMore()).isFalse();
                if (firstPath == WritePath.MISSION || secondPath == WritePath.MISSION) {
                    var sent = announcements.getById(announcement.id());
                    assertThat(sent.status()).isEqualTo(MissionAnnouncementStatus.SENT);
                    assertThat(sent.sentMessageId()).isEqualTo(firstPath == WritePath.MISSION ? firstId : secondId);
                }
            } finally {
                gate.release.countDown(); // 실패해도 미커밋 트랜잭션을 남기지 않는다.
            }
        }
    }

    private long write(WritePath path) {
        return switch (path) {
            case TEXT -> chats.sendMessage(chatId, sender, "텍스트 전송").messageId();
            case PHOTO -> photos.send(chatId, sender, UUID.randomUUID().toString(), image, "image/png").messageId();
            case VIDEO_REPLY -> videoReplies.send(roomId, videoId, sender, "영상 답장", UUID.randomUUID().toString()).messageId();
            case SYSTEM -> systemMessages.sendAsSystem(chatId, "운영 안내", null).id();
            case MISSION -> {
                missionStep.send(announcement, chatId);
                yield announcements.getById(announcement.id()).sentMessageId();
            }
        };
    }

    private int databaseSession() {
        return jdbc.queryForObject("select pg_backend_pid()", Integer.class);
    }

    private boolean isBlockedBy(int waitingPid, int holdingPid) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select ? = any(pg_blocking_pids(?))", Boolean.class, holdingPid, waitingPid));
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(20, TimeUnit.SECONDS)) throw new AssertionError("beforeCommit gate 대기 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static class CommitGate {
        final CompletableFuture<SavedMessage> saved = new CompletableFuture<>();
        final CountDownLatch release = new CountDownLatch(1);
    }

    private static class WriteObservation {
        final CompletableFuture<Integer> session = new CompletableFuture<>();
        final AtomicBoolean saveEntered = new AtomicBoolean();
    }

    private record SavedMessage(long id, int pid) {}
}
