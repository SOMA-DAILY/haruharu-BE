package org.son.haruharu.domain.video;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.son.haruharu.domain.video.business.VideoService;
import org.son.haruharu.domain.video.business.VideoUploadSessionService;
import org.son.haruharu.domain.video.business.command.VideoUploadCommand;
import org.son.haruharu.domain.video.business.dto.VideoUploadSessionResponse;
import org.son.haruharu.domain.video.business.exception.DuplicateSlotUploadException;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoUploadSession;
import org.son.haruharu.domain.video.implement.VideoUploadSessionRepository;
import org.son.haruharu.domain.video.implement.exception.VideoUploadUnavailableException;
import org.son.haruharu.domain.video.storage.VideoPostJpaRepository;
import org.son.haruharu.infrastructure.s3.PresignedUpload;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class VideoUploadSessionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    private final VideoUploadSessionService service;
    private final VideoService legacy;
    private final VideoUploadSessionRepository sessions;
    private final RoomJpaRepository rooms;
    private final RoomMemberJpaRepository members;
    private final VideoPostJpaRepository posts;
    private final MockMvc mvc;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    VideoUploadSessionIntegrationTest(VideoUploadSessionService service, VideoService legacy,
                                      VideoUploadSessionRepository sessions, RoomJpaRepository rooms,
                                      RoomMemberJpaRepository members, VideoPostJpaRepository posts,
                                      MockMvc mvc, PlatformTransactionManager transactionManager,
                                      JdbcTemplate jdbc, Clock clock) {
        this.service = service;
        this.legacy = legacy;
        this.sessions = sessions;
        this.rooms = rooms;
        this.members = members;
        this.posts = posts;
        this.mvc = mvc;
        this.transactionManager = transactionManager;
        this.jdbc = jdbc;
        this.clock = clock;
    }
    @MockitoBean S3StoragePort storage;

    private final Long user = 9100001L;
    private final Long other = 9100002L;
    private Long room;
    private Long member;

    @BeforeEach
    void setup() {
        LocalDateTime now = LocalDateTime.now(clock);
        room = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(1), now.plusDays(1),
                now.plusDays(1).plusHours(1), now.plusDays(1).plusHours(2), now.plusDays(2), 3))).toDomain().id();
        member = members.save(RoomMemberEntity.from(RoomMember.of(room, user, "MALE", null, now))).toDomain().id();
        members.save(RoomMemberEntity.from(RoomMember.of(room, other, "FEMALE", null, now)));
        when(storage.publicBaseUrl()).thenReturn("https://test/");
        when(storage.head(anyString())).thenAnswer(invocation -> new S3StoragePort.ObjectMetadata(1000,
                invocation.<String>getArgument(0).endsWith(".jpg") ? "image/jpeg" : "video/mp4", "etag"));
        when(storage.createPresignedGetUrl(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void givenHundredCharacterCaption_whenReservedAndPublished_thenBothStorageStagesPreserveIt() throws Exception {
        UUID id = UUID.randomUUID();
        String caption = "가".repeat(100);
        mvc.perform(post("/api/v1/rooms/" + room + "/video-upload-sessions")
                .with(as(user)).contentType("application/json")
                .content("{\"job_id\":\"" + id + "\",\"caption\":\"" + caption + "\"}"))
                .andExpect(status().isOk());

        assertThat(sessions.findById(id).orElseThrow().caption()).isEqualTo(caption);
        Long postId = service.complete(user, room, id).videoPostId();
        assertThat(posts.findById(postId).orElseThrow().toDomain().caption()).isEqualTo(caption);
    }

    @Test
    void givenOverlongCaption_whenReserved_thenRejectsBeforeCreatingSession() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/rooms/" + room + "/video-upload-sessions")
                .with(as(user)).contentType("application/json")
                .content("{\"job_id\":\"" + id + "\",\"caption\":\"" + "가".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(sessions.findById(id)).isEmpty();
    }

    @Test
    void givenSameJob_whenCompletedConcurrently_thenOnePostAndOneResult() throws Exception {
        UUID id = UUID.randomUUID();
        VideoUploadSessionResponse accepted = service.reserve(user, room, id, "인사");

        List<Object> results = race(
                () -> service.complete(user, room, id).videoPostId(),
                () -> service.complete(user, room, id).videoPostId());

        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(activePosts()).hasSize(1);
        VideoPost saved = posts.findById((Long) results.get(0)).orElseThrow().toDomain();
        assertThat(saved.slotStart()).isEqualTo(accepted.slotStart());
        assertThat(saved.feedVideoUrl()).contains("/published/feed.mp4");
        verify(storage, times(3)).promote(anyString(), contains("/published/"), eq("etag"));

        legacy.delete(user, saved.id());
        assertThat(service.complete(user, room, id).videoPostId()).isEqualTo(saved.id());
        assertThat(activePosts()).isEmpty();
    }

    @Test
    void givenDifferentJobs_whenReservedConcurrently_thenExactlyOneStaysPending() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        List<Object> results = race(
                () -> service.reserve(user, room, first, null),
                () -> service.reserve(user, room, second, null));

        assertThat(results).noneMatch(Exception.class::isInstance);
        assertThat(service.listPending(user, room)).hasSize(1);
        assertThat(List.of(first, second).stream()
                .filter(id -> sessions.findById(id).orElseThrow().isCancelled()).count()).isEqualTo(1);
    }

    @Test
    void givenUnpublishedReservation_whenAnotherJobReservedInSameSlot_thenReplacesIt() {
        UUID stalled = UUID.randomUUID();
        UUID retried = UUID.randomUUID();
        service.reserve(user, room, stalled, null);

        VideoUploadSessionResponse accepted = service.reserve(user, room, retried, null);

        assertThat(accepted.status()).isEqualTo("pending");
        assertThat(sessions.findById(stalled).orElseThrow().isCancelled()).isTrue();
        assertThatThrownBy(() -> service.presign(user, room, stalled, "feed", null))
                .isInstanceOf(VideoUploadUnavailableException.class);
        assertThatThrownBy(() -> service.complete(user, room, stalled))
                .isInstanceOf(VideoUploadUnavailableException.class);
        assertThat(service.complete(user, room, retried).videoPostId()).isNotNull();
        assertThat(activePosts()).hasSize(1);
    }

    @Test
    void givenPendingReservationInEarlierSlot_whenReservingCurrentSlot_thenEarlierOneStaysPublishable() {
        LocalDateTime now = LocalDateTime.now(clock);
        // 같은 방의 다른 멤버 접수로 현재 슬롯만 알아낸다. 내 세션은 건드리지 않는다.
        LocalDateTime currentSlot = service.reserve(other, room, UUID.randomUUID(), null).slotStart();
        UUID earlier = store(user, room, member, currentSlot.minusHours(1), now);

        service.reserve(user, room, UUID.randomUUID(), null);

        assertThat(sessions.findById(earlier).orElseThrow().isCancelled()).isFalse();
        assertThat(service.listPending(user, room)).hasSize(2);
        assertThat(service.complete(user, room, earlier).slotStart()).isEqualTo(currentSlot.minusHours(1));
    }

    @Test
    void givenPublishedPost_whenAnotherJobReservedInSameSlot_thenRejectsAsAlreadyUploaded() {
        UUID published = UUID.randomUUID();
        UUID retried = UUID.randomUUID();
        service.reserve(user, room, published, null);
        service.complete(user, room, published);

        assertThatThrownBy(() -> service.reserve(user, room, retried, null))
                .isInstanceOf(DuplicateSlotUploadException.class);

        assertThat(sessions.findById(retried)).isEmpty();
        assertThat(activePosts()).hasSize(1);
    }

    @Test
    void givenCompletionInProgress_whenNewReservationArrives_thenWaitsAndIsRejectedByThePublishedPost() throws Exception {
        UUID publishing = UUID.randomUUID();
        UUID retried = UUID.randomUUID();
        service.reserve(user, room, publishing, null);
        CountDownLatch promoting = new CountDownLatch(1);
        CountDownLatch allowPromote = new CountDownLatch(1);
        doAnswer(invocation -> {
            promoting.countDown();
            if (!allowPromote.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("promote timed out");
            }
            return null;
        }).when(storage).promote(anyString(), anyString(), anyString());

        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Long> completion = pool.submit(() -> service.complete(user, room, publishing).videoPostId());
            assertThat(promoting.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> reservation = pool.submit(() -> service.reserve(user, room, retried, null));
            try {
                // 게시 트랜잭션이 멤버 락을 쥐고 있어 새 접수는 게시 결과가 정해질 때까지 기다린다.
                assertThatThrownBy(() -> reservation.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                allowPromote.countDown();
            }
            Long postId = completion.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> reservation.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(DuplicateSlotUploadException.class);
            VideoUploadSession published = sessions.findById(publishing).orElseThrow();
            assertThat(published.postId()).isEqualTo(postId);
            assertThat(published.isCancelled()).isFalse();
            assertThat(sessions.findById(retried)).isEmpty();
            assertThat(activePosts()).hasSize(1);
        }
    }

    @Test
    void givenCompletionRacingWithNewReservation_whenBothRun_thenSlotKeepsOnePost() throws Exception {
        UUID stalled = UUID.randomUUID();
        UUID retried = UUID.randomUUID();
        service.reserve(user, room, stalled, null);

        List<Object> results = race(
                () -> service.complete(user, room, stalled),
                () -> service.reserve(user, room, retried, null));

        assertThat(results.stream().filter(Exception.class::isInstance).count()).isEqualTo(1);
        if (results.get(0) instanceof Exception) {
            // 새 접수가 먼저 처리됐다. 멈춘 접수는 취소됐고 새 접수만 게시할 수 있다.
            assertThat(sessions.findById(stalled).orElseThrow().isCancelled()).isTrue();
            assertThat(service.complete(user, room, retried).videoPostId()).isNotNull();
        } else {
            VideoUploadSession published = sessions.findById(stalled).orElseThrow();
            assertThat(published.postId()).isNotNull();
            assertThat(published.isCancelled()).isFalse();
            assertThat(sessions.findById(retried)).isEmpty();
        }
        assertThat(activePosts()).hasSize(1);
    }

    @Test
    void givenSessionsInEveryState_whenListingMine_thenReturnsOnlyMyPendingOnesInSlotOrder() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        UUID current = UUID.randomUUID();
        LocalDateTime currentSlot = service.reserve(user, room, current, null).slotStart();
        UUID earlier = store(user, room, member, currentSlot.minusHours(1), now);
        store(user, room, member, currentSlot.minusHours(2), now.minusHours(3));
        UUID cancelled = store(user, room, member, currentSlot.minusHours(3), now);
        sessions.save(sessions.findById(cancelled).orElseThrow().cancelled());
        UUID published = store(user, room, member, currentSlot.minusHours(4), now);
        // 테스트 스키마에는 post_id FK가 없어 게시물 없이 게시 완료 상태만 만든다.
        sessions.save(sessions.findById(published).orElseThrow().completed(1L));
        service.reserve(other, room, UUID.randomUUID(), null);
        Long anotherRoom = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(1), now.plusDays(1),
                now.plusDays(1).plusHours(1), now.plusDays(1).plusHours(2), now.plusDays(2), 3))).toDomain().id();
        members.save(RoomMemberEntity.from(RoomMember.of(anotherRoom, user, "MALE", null, now)));
        service.reserve(user, anotherRoom, UUID.randomUUID(), null);

        mvc.perform(get("/api/v1/rooms/" + room + "/video-upload-sessions").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(earlier.toString()))
                .andExpect(jsonPath("$.data[0].status").value("pending"))
                .andExpect(jsonPath("$.data[0].slot_start").exists())
                .andExpect(jsonPath("$.data[1].id").value(current.toString()));
    }

    @Test
    void givenNonMember_whenListingSessions_thenForbidden() throws Exception {
        mvc.perform(get("/api/v1/rooms/" + room + "/video-upload-sessions").with(as(9100003L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ROOM_403_NOT_MEMBER"));
    }

    @Test
    void givenSameIdDifferentOwners_whenReservedConcurrently_thenOwnerCannotBeOverwritten() throws Exception {
        UUID id = UUID.randomUUID();

        List<Object> results = race(
                () -> service.reserve(user, room, id, null),
                () -> service.reserve(other, room, id, null));

        assertThat(results.stream().filter(Exception.class::isInstance).count()).isEqualTo(1);
        Long winner = sessions.findById(id).orElseThrow().userId();
        Long loser = winner.equals(user) ? other : user;
        assertThat(service.get(winner, room, id).id()).isEqualTo(id);
        assertThatThrownBy(() -> service.get(loser, room, id)).hasMessageContaining("본인");
        assertThat(sessions.findById(id).orElseThrow().userId()).isEqualTo(winner);
    }

    @Test
    void givenLegacyAndNewClient_whenPublishingSameSlot_thenOnlyOnePost() throws Exception {
        UUID id = UUID.randomUUID();
        service.reserve(user, room, id, null);

        List<Object> results = race(
                () -> service.complete(user, room, id),
                () -> legacy.upload(user, room, legacyCommand()));

        assertThat(results.stream().filter(Exception.class::isInstance).count()).isEqualTo(1);
        assertThat(activePosts()).hasSize(1);
    }

    @Test
    void givenLegacyPostAfterReservation_whenCompleting_thenDoesNotPromoteUnpublishedAssets() {
        UUID id = UUID.randomUUID();
        service.reserve(user, room, id, null);
        legacy.upload(user, room, legacyCommand());

        assertThatThrownBy(() -> service.complete(user, room, id)).hasMessageContaining("이미");

        verify(storage, never()).promote(anyString(), anyString(), anyString());
    }

    @Test
    void givenPromotedAssets_whenDatabaseTransactionRollsBack_thenDeletesPublishedCopies() {
        UUID id = UUID.randomUUID();
        service.reserve(user, room, id, null);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            service.complete(user, room, id);
            throw new IllegalStateException("rollback after promotion");
        })).isInstanceOf(IllegalStateException.class);

        verify(storage).deleteObjectsOrThrow(List.of(
                "users/" + user + "/videos/" + id + "/published/viewer.mp4",
                "users/" + user + "/videos/" + id + "/published/feed.mp4",
                "users/" + user + "/videos/" + id + "/published/thumbnail.jpg"));
        assertThat(activePosts()).isEmpty();
        assertThat(sessions.findById(id).orElseThrow().postId()).isNull();
    }

    @Test
    void givenRollbackCleanup_whenRetryStarts_thenCleanupFinishesBeforeRetryCopies() throws Exception {
        UUID id = UUID.randomUUID();
        service.reserve(user, room, id, null);
        CountDownLatch cleanupStarted = new CountDownLatch(1);
        CountDownLatch allowCleanup = new CountDownLatch(1);
        CountDownLatch retryStarted = new CountDownLatch(1);
        AtomicInteger copies = new AtomicInteger();
        doAnswer(invocation -> {
            copies.incrementAndGet();
            return null;
        }).when(storage).promote(anyString(), anyString(), anyString());
        doAnswer(invocation -> {
            cleanupStarted.countDown();
            if (!allowCleanup.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("cleanup timed out");
            }
            return null;
        }).when(storage).deleteObjectsOrThrow(anyList());

        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> failed = pool.submit(() -> {
                try {
                    new TransactionTemplate(transactionManager).execute(status -> {
                        service.complete(user, room, id);
                        throw new IllegalStateException("rollback after promotion");
                    });
                } catch (IllegalStateException expected) {
                    return;
                }
                throw new AssertionError("expected rollback");
            });
            assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var retry = pool.submit(() -> {
                retryStarted.countDown();
                return service.complete(user, room, id);
            });
            assertThat(retryStarted.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> retry.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                assertThat(copies.get()).isEqualTo(3);
            } finally {
                allowCleanup.countDown();
            }
            failed.get(5, TimeUnit.SECONDS);
            assertThat(retry.get(5, TimeUnit.SECONDS).videoPostId()).isNotNull();
            assertThat(copies.get()).isEqualTo(6);
        }
    }

    @Test
    void givenMissingAsset_whenComplete_thenNoPostAndRetrySucceeds() {
        UUID id = UUID.randomUUID();
        service.reserve(user, room, id, null);
        when(storage.head(contains("thumbnail"))).thenReturn(null);

        assertThatThrownBy(() -> service.complete(user, room, id)).hasMessageContaining("확인");
        assertThat(activePosts()).isEmpty();

        when(storage.head(contains("thumbnail"))).thenReturn(new S3StoragePort.ObjectMetadata(1000, "image/jpeg", "etag"));
        assertThat(service.complete(user, room, id).videoPostId()).isNotNull();
    }

    @Test
    void givenSession_whenOtherMemberAccessesHttp_thenForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        service.reserve(user, room, id, null);

        mvc.perform(get(sessionPath(id)).with(as(other))).andExpect(status().isForbidden());
        mvc.perform(post(sessionPath(id) + "/complete").with(as(other))).andExpect(status().isForbidden());
    }

    @Test
    void givenQuickTimeViewerAndLegacyFeed_whenPresigning_thenUsesRequestedOrDefaultContentType() throws Exception {
        UUID id = UUID.randomUUID();
        service.reserve(user, room, id, null);
        when(storage.head(contains("viewer"))).thenReturn(null);
        when(storage.head(contains("feed"))).thenReturn(null);
        when(storage.createPresignedPutUrl(anyString(), anyString()))
                .thenReturn(new PresignedUpload("https://presigned-put", "https://unused"));

        mvc.perform(post(sessionPath(id) + "/assets/viewer/presign").with(as(user))
                        .contentType("application/json").content("{\"content_type\":\"video/quicktime\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presigned_url").value("https://presigned-put"));
        mvc.perform(post(sessionPath(id) + "/assets/feed/presign").with(as(user)))
                .andExpect(status().isOk());

        verify(storage).createPresignedPutUrl(contains("viewer.mp4"), eq("video/quicktime"));
        verify(storage).createPresignedPutUrl(contains("feed.mp4"), eq("video/mp4"));
    }

    @Test
    void givenUnknownVariant_whenPresigning_thenRejectsAsInvalidAsset() throws Exception {
        UUID id = UUID.randomUUID();
        service.reserve(user, room, id, null);

        mvc.perform(post(sessionPath(id) + "/assets/original/presign").with(as(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VIDEO_UPLOAD_INVALID_ASSET"));

        verify(storage, never()).createPresignedPutUrl(anyString(), anyString());
    }

    @Test
    void givenPreviouslyAcceptedSlot_whenCompletedNow_thenKeepsOriginalSlot() {
        UUID id = UUID.randomUUID();
        VideoUploadSessionResponse accepted = service.reserve(user, room, id, null);
        jdbc.update("update video_upload_sessions set slot_start = slot_start - interval '1 hour' where id = ?", id);

        assertThat(service.complete(user, room, id).slotStart()).isEqualTo(accepted.slotStart().minusHours(1));
    }

    @Test
    void givenExpiredOrCancelledSession_whenComplete_thenNoPublication() {
        UUID expired = UUID.randomUUID();
        service.reserve(user, room, expired, null);
        jdbc.update("update video_upload_sessions set expires_at = expires_at - interval '3 hours' where id = ?", expired);

        assertThatThrownBy(() -> service.complete(user, room, expired)).hasMessageContaining("만료");
        assertThat(activePosts()).isEmpty();

        UUID cancelled = UUID.randomUUID();
        service.reserve(user, room, cancelled, null);
        service.cancel(user, room, cancelled);
        assertThatThrownBy(() -> service.complete(user, room, cancelled)).hasMessageContaining("취소");
        assertThat(sessions.findById(cancelled).map(VideoUploadSession::isCancelled)).contains(true);
        verify(storage, never()).promote(anyString(), anyString(), anyString());
    }

    /** 접수 시각을 직접 정해 세션 행을 만든다(만료는 접수 2시간 뒤). */
    private UUID store(Long userId, Long roomId, Long memberId, LocalDateTime slotStart, LocalDateTime reservedAt) {
        UUID id = UUID.randomUUID();
        sessions.save(VideoUploadSession.create(id, userId, roomId, memberId, slotStart, reservedAt, null));
        return id;
    }

    private List<?> activePosts() {
        return posts.findByRoomMemberIdAndDeletedAtIsNullAndIsHiddenFalseOrderBySlotStartAsc(member);
    }

    private VideoUploadCommand legacyCommand() {
        return new VideoUploadCommand("https://test/users/" + user + "/videos/legacy.mp4", null, null);
    }

    private String sessionPath(UUID id) {
        return "/api/v1/rooms/" + room + "/video-upload-sessions/" + id;
    }

    private RequestPostProcessor as(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    /** 두 작업을 동시에 시작해 결과(또는 던진 예외)를 순서대로 돌려준다. */
    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> action : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    gate.await();
                    try {
                        return action.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            gate.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }
}
