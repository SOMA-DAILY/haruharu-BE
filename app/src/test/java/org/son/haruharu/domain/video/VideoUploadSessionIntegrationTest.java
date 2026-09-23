package org.son.haruharu.domain.video;

import org.junit.jupiter.api.*;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.storage.*;
import org.son.haruharu.domain.video.business.*;
import org.son.haruharu.domain.video.business.command.VideoUploadCommand;
import org.son.haruharu.domain.video.implement.*;
import org.son.haruharu.domain.video.storage.*;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.son.haruharu.infrastructure.s3.PresignedUpload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class VideoUploadSessionIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);
        r.add("spring.datasource.password",POSTGRES::getPassword);r.add("spring.datasource.driver-class-name",POSTGRES::getDriverClassName);
        r.add("spring.jpa.hibernate.ddl-auto",()->"create-drop");
        r.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired VideoUploadSessionService service;
    @Autowired VideoService legacy;
    @Autowired VideoUploadSessionRepository sessions;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired VideoPostJpaRepository posts;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean S3StoragePort storage;
    @Autowired Clock clock;
    Long room,member,otherMember;
    final Long user=9100001L,other=9100002L;
    @BeforeEach void setup() {
        var now=LocalDateTime.now(clock);
        room=rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(1),now.plusDays(1),now.plusDays(1).plusHours(1),now.plusDays(1).plusHours(2),now.plusDays(2),3))).toDomain().id();
        member=members.save(RoomMemberEntity.from(RoomMember.of(room,user,"MALE",null,now))).toDomain().id();
        otherMember=members.save(RoomMemberEntity.from(RoomMember.of(room,other,"FEMALE",null,now))).toDomain().id();
        when(storage.publicBaseUrl()).thenReturn("https://test/");
        when(storage.head(anyString())).thenAnswer(i->new S3StoragePort.ObjectMetadata(1000,i.<String>getArgument(0).endsWith(".jpg")?"image/jpeg":"video/mp4","etag"));
        when(storage.createPresignedGetUrl(any())).thenAnswer(i->i.getArgument(0));
    }
    @Test void givenSameJob_whenCompletedConcurrently_thenOnePostAndOneResult() throws Exception {
        var id=UUID.randomUUID();var accepted=service.reserve(user,room,id,"인사");
        var results=race(()->service.complete(user,room,id).videoPostId(),()->service.complete(user,room,id).videoPostId());
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(posts.findByRoomMemberIdAndDeletedAtIsNullAndIsHiddenFalseOrderBySlotStartAsc(member)).hasSize(1);
        var saved=posts.findById((Long)results.get(0)).orElseThrow().toDomain();
        assertThat(saved.slotStart()).isEqualTo(accepted.slotStart());
        assertThat(saved.feedVideoUrl()).contains("/published/feed.mp4");
        verify(storage,times(3)).promote(anyString(),contains("/published/"),eq("etag"));
        legacy.delete(user,saved.id());
        assertThat(service.complete(user,room,id).videoPostId()).isEqualTo(saved.id());
        assertThat(posts.findByRoomMemberIdAndDeletedAtIsNullAndIsHiddenFalseOrderBySlotStartAsc(member)).isEmpty();
    }
    @Test void givenDifferentJobs_whenReservedConcurrently_thenOneReservation() throws Exception {
        var results=race(()->service.reserve(user,room,UUID.randomUUID(),null),()->service.reserve(user,room,UUID.randomUUID(),null));
        assertThat(results.stream().filter(v->v instanceof Exception).count()).isEqualTo(1);
    }
    @Test void givenSameIdDifferentOwners_whenReservedConcurrently_thenOwnerCannotBeOverwritten() throws Exception {
        var id=UUID.randomUUID();
        var results=race(()->service.reserve(user,room,id,null),()->service.reserve(other,room,id,null));
        assertThat(results.stream().filter(v->v instanceof Exception).count()).isEqualTo(1);
        var saved=sessions.find(id).orElseThrow();
        Long winner=saved.userId(),loser=winner.equals(user)?other:user;
        assertThat(service.get(winner,room,id).id()).isEqualTo(id);
        assertThatThrownBy(()->service.get(loser,room,id)).hasMessageContaining("본인");
        assertThat(sessions.find(id).orElseThrow().userId()).isEqualTo(winner);
    }
    @Test void givenLegacyAndNewClient_whenPublishingSameSlot_thenOnlyOnePost() throws Exception {
        var id=UUID.randomUUID();service.reserve(user,room,id,null);
        var results=race(()->service.complete(user,room,id),()->legacy.upload(user,room,new VideoUploadCommand("https://test/users/"+user+"/videos/legacy.mp4",null,null)));
        assertThat(results.stream().filter(v->v instanceof Exception).count()).isEqualTo(1);
        assertThat(posts.findByRoomMemberIdAndDeletedAtIsNullAndIsHiddenFalseOrderBySlotStartAsc(member)).hasSize(1);
    }
    @Test void givenLegacyPostAfterReservation_whenCompleting_thenDoesNotPromoteUnpublishedAssets() {
        var id=UUID.randomUUID();service.reserve(user,room,id,null);
        legacy.upload(user,room,new VideoUploadCommand("https://test/users/"+user+"/videos/legacy.mp4",null,null));
        assertThatThrownBy(()->service.complete(user,room,id)).hasMessageContaining("이미");
        verify(storage,never()).promote(anyString(),anyString(),anyString());
    }
    @Test void givenPromotedAssets_whenDatabaseTransactionRollsBack_thenDeletesPublishedCopies() {
        var id=UUID.randomUUID();service.reserve(user,room,id,null);
        assertThatThrownBy(()->new TransactionTemplate(transactionManager).execute(status->{
            service.complete(user,room,id);
            throw new IllegalStateException("rollback after promotion");
        })).isInstanceOf(IllegalStateException.class);
        verify(storage).deleteObjects(List.of("users/"+user+"/videos/"+id+"/published/viewer.mp4",
                "users/"+user+"/videos/"+id+"/published/feed.mp4",
                "users/"+user+"/videos/"+id+"/published/thumbnail.jpg"));
        assertThat(posts.findByRoomMemberIdAndDeletedAtIsNullAndIsHiddenFalseOrderBySlotStartAsc(member)).isEmpty();
        assertThat(sessions.find(id).orElseThrow().postId()).isNull();
    }
    @Test void givenRollbackCleanup_whenRetryStarts_thenCleanupFinishesBeforeRetryCopies() throws Exception {
        var id=UUID.randomUUID();service.reserve(user,room,id,null);
        var cleanupStarted=new CountDownLatch(1);
        var allowCleanup=new CountDownLatch(1);
        var retryStarted=new CountDownLatch(1);
        var copies=new AtomicInteger();
        doAnswer(invocation->{copies.incrementAndGet();return null;})
                .when(storage).promote(anyString(),anyString(),anyString());
        doAnswer(invocation->{
            cleanupStarted.countDown();
            if (!allowCleanup.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("cleanup timed out");
            return null;
        }).when(storage).deleteObjects(anyList());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var failed=pool.submit(()->{
                try {
                    new TransactionTemplate(transactionManager).execute(status->{
                        service.complete(user,room,id);
                        throw new IllegalStateException("rollback after promotion");
                    });
                } catch (IllegalStateException expected) { return; }
                throw new AssertionError("expected rollback");
            });
            assertThat(cleanupStarted.await(5,TimeUnit.SECONDS)).isTrue();
            var retry=pool.submit(()->{retryStarted.countDown();return service.complete(user,room,id);});
            assertThat(retryStarted.await(5,TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(()->retry.get(1,TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
                assertThat(copies.get()).isEqualTo(3);
            } finally {
                allowCleanup.countDown();
            }
            failed.get(5,TimeUnit.SECONDS);
            assertThat(retry.get(5,TimeUnit.SECONDS).videoPostId()).isNotNull();
            assertThat(copies.get()).isEqualTo(6);
        }
    }
    @Test void givenMissingAsset_whenComplete_thenNoPostAndRetrySucceeds() {
        var id=UUID.randomUUID();service.reserve(user,room,id,null);
        when(storage.head(contains("thumbnail"))).thenReturn(null);
        assertThatThrownBy(()->service.complete(user,room,id)).hasMessageContaining("확인");
        assertThat(posts.findByRoomMemberIdAndDeletedAtIsNullAndIsHiddenFalseOrderBySlotStartAsc(member)).isEmpty();
        when(storage.head(contains("thumbnail"))).thenReturn(new S3StoragePort.ObjectMetadata(1000,"image/jpeg","etag"));
        assertThat(service.complete(user,room,id).videoPostId()).isNotNull();
    }
    @Test void givenSession_whenOtherMemberAccessesHttp_thenForbidden() throws Exception {
        var id=UUID.randomUUID();service.reserve(user,room,id,null);
        mvc.perform(get("/api/v1/rooms/"+room+"/video-upload-sessions/"+id)
                .with(authentication(new UsernamePasswordAuthenticationToken(other,null,List.of())))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/rooms/"+room+"/video-upload-sessions/"+id+"/complete")
                .with(authentication(new UsernamePasswordAuthenticationToken(other,null,List.of())))).andExpect(status().isForbidden());
    }
    @Test void givenQuickTimeViewerAndLegacyFeed_whenPresigning_thenUsesRequestedOrDefaultContentType() throws Exception {
        var id=UUID.randomUUID();service.reserve(user,room,id,null);
        when(storage.head(contains("viewer"))).thenReturn(null);
        when(storage.head(contains("feed"))).thenReturn(null);
        when(storage.createPresignedPutUrl(anyString(),anyString()))
                .thenReturn(new PresignedUpload("https://presigned-put","https://unused"));
        var auth=authentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
        mvc.perform(post("/api/v1/rooms/"+room+"/video-upload-sessions/"+id+"/assets/viewer/presign")
                .with(auth).contentType("application/json").content("{\"content_type\":\"video/quicktime\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.presigned_url").value("https://presigned-put"));
        mvc.perform(post("/api/v1/rooms/"+room+"/video-upload-sessions/"+id+"/assets/feed/presign")
                .with(auth)).andExpect(status().isOk());
        verify(storage).createPresignedPutUrl(contains("viewer.mp4"),eq("video/quicktime"));
        verify(storage).createPresignedPutUrl(contains("feed.mp4"),eq("video/mp4"));
    }
    @Test void givenPreviouslyAcceptedSlot_whenCompletedNow_thenKeepsOriginalSlot() {
        var id=UUID.randomUUID();var accepted=service.reserve(user,room,id,null);
        jdbc.update("update video_upload_sessions set slot_start=slot_start - interval '1 hour' where id=?",id);
        var result=service.complete(user,room,id);
        assertThat(result.slotStart()).isEqualTo(accepted.slotStart().minusHours(1));
    }
    @Test void givenExpiredOrCancelledSession_whenComplete_thenNoPublication() {
        var id=UUID.randomUUID();service.reserve(user,room,id,null);
        jdbc.update("update video_upload_sessions set expires_at=expires_at - interval '3 hours' where id=?",id);
        assertThatThrownBy(()->service.complete(user,room,id)).hasMessageContaining("만료");
        assertThat(posts.findByRoomMemberIdAndDeletedAtIsNullAndIsHiddenFalseOrderBySlotStartAsc(member)).isEmpty();
        var next=UUID.randomUUID();service.reserve(user,room,next,null);service.cancel(user,room,next);
        assertThatThrownBy(()->service.complete(user,room,next)).hasMessageContaining("취소");
        verify(storage,never()).promote(anyString(),anyString(),anyString());
    }
    private List<Object> race(Callable<?> first,Callable<?> second) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            List<Future<Object>> futures=new ArrayList<>();
            for(var action:List.of(first,second)) futures.add(pool.submit(()->{gate.await();try{return action.call();}catch(Exception e){return e;}}));
            gate.countDown();return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        }
    }
}
