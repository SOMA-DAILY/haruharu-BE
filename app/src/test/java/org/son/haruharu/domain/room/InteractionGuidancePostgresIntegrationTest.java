package org.son.haruharu.domain.room;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.chat.business.VideoReplyService;
import org.son.haruharu.domain.room.business.InteractionGuidanceService;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.business.command.ClaimInteractionGuideCommand;
import org.son.haruharu.domain.room.business.dto.InteractionGuideClaimResponse;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.storage.*;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.*;
import org.son.haruharu.domain.video.business.VideoReactionService;
import org.son.haruharu.domain.video.implement.*;
import org.son.haruharu.domain.video.storage.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(properties = {
        "haruharu.interaction-guidance.enabled=true",
        "haruharu.interaction-guidance.enrollment-starts-at=2026-01-01T00:00:00",
        "haruharu.interaction-guidance.tooltip-percent=100"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InteractionGuidancePostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired VideoPostJpaRepository videos;
    @Autowired UserJpaRepository users;
    @Autowired ChatService chats;
    @Autowired VideoReactionService reactions;
    @Autowired VideoReplyService replies;
    @Autowired InteractionGuidanceService guidance;
    @Autowired PlatformTransactionManager tx;
    @Autowired Clock clock;
    @Autowired ApplicationEventPublisher events;
    Long roomId, sender, owner, senderMemberId, ownerMemberId, videoId, chatId;
    LocalDateTime now;

    @BeforeEach
    void setup() {
        now = LocalDateTime.now(clock);
        roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(2), now.plusDays(2),
                now.plusDays(2).plusHours(1), now.plusDays(2).plusHours(2), now.plusDays(3), 3))).toDomain().id();
        sender = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        owner = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        senderMemberId = member(sender);
        ownerMemberId = member(owner);
        chats.createGroupRoom(roomId);
        chatId = chats.getGroupRoomBySourceRoom(roomId, sender).chatRoomId();
        videoId = video(ownerMemberId, now.minusHours(2));
    }

    Long member(Long user) {
        return members.save(RoomMemberEntity.from(RoomMember.of(roomId, user, "MALE", null, now)))
                .toDomain().id();
    }

    Long video(Long member, LocalDateTime slot) {
        return videos.save(VideoPostEntity.from(VideoPost.create(member, slot, null, "테스트", "video", "thumb", now)))
                .toDomain().id();
    }

    ClaimInteractionGuideCommand command(InteractionGuideKind kind, String session) {
        return new ClaimInteractionGuideCommand(kind, videoId, session);
    }

    long claims() {
        return jdbc.queryForObject("select count(*) from room_interaction_guide_claims where room_member_id=?",
                Long.class, senderMemberId);
    }

    UsernamePasswordAuthenticationToken auth(Long id) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of());
    }

    @Test
    @DisplayName("상태 조회는 배정만 고정하고 안내를 소비하지 않으며 JSON 계약을 지킨다")
    void givenUnengagedMember_whenReadRepeatedly_thenNoGuideConsumed() throws Exception {
        var first = guidance.get(roomId, sender);
        assertThat(first.variant()).isEqualTo(InteractionExperimentVariant.TOOLTIP);
        assertThat(first.nextGuide()).isEqualTo(InteractionGuideKind.REACTION);
        assertThat(guidance.get(roomId, owner).assignedAt()).isEqualTo(first.assignedAt());
        assertThat(claims()).isZero();
        mvc.perform(get("/api/v1/rooms/{id}/interaction-guidance", roomId).with(authentication(auth(sender))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.experiment_key").value("first-video-interaction-v1"))
                .andExpect(jsonPath("$.data.variant").value("TOOLTIP"))
                .andExpect(jsonPath("$.data.eligible_at").exists())
                .andExpect(jsonPath("$.data.first_reacted_at").isEmpty())
                .andExpect(jsonPath("$.data.next_guide").value("REACTION"));
    }

    @Test
    @DisplayName("여러 기기가 같은 종류를 동시에 확보해도 한 기기만 성공한다")
    void givenManyDevices_whenClaimSameKindConcurrently_thenOneClaim() throws Exception {
        var results = race(8, i -> guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "device-" + i)));
        assertThat(results.stream().filter(InteractionGuideClaimResponse::claimed)).hasSize(1);
        assertThat(claims()).isOne();
    }

    @Test
    @DisplayName("같은 종류·세션의 동시 재시도는 동일한 claim을 돌려준다")
    void givenSameRequest_whenRetriedConcurrently_thenSameId() throws Exception {
        var results = race(6, i -> guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "retry")));
        assertThat(results).allMatch(InteractionGuideClaimResponse::claimed);
        assertThat(results.stream().map(InteractionGuideClaimResponse::claimId).distinct()).hasSize(1);
        assertThat(claims()).isOne();
    }

    @Test
    @DisplayName("반응 안내를 소비한 세션에서는 답장 안내를 확보하지 않는다")
    void givenConsumedSession_whenClaimOtherKind_thenDeniedButNextSessionAllowed() {
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "viewer")).claimed()).isTrue();
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REPLY, "viewer")).reason())
                .isEqualTo("SESSION_ALREADY_CONSUMED");
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REPLY, "next-viewer")).claimed()).isTrue();
        assertThat(claims()).isEqualTo(2);
    }

    @Test
    @DisplayName("한 세션에서 다른 종류가 경쟁해도 안내는 하나만 확보된다")
    void givenTwoKinds_whenClaimConcurrently_thenOnePerSession() throws Exception {
        var results = race(8, i -> guidance.claim(roomId, sender,
                command(i % 2 == 0 ? InteractionGuideKind.REACTION : InteractionGuideKind.REPLY, "one-viewer")));
        assertThat(results.stream().filter(InteractionGuideClaimResponse::claimed).map(InteractionGuideClaimResponse::kind).distinct())
                .hasSize(1);
        assertThat(claims()).isOne();
    }

    @Test
    @DisplayName("반응 성공 후 취소해도 최초 성공 시각이 남고 반응 안내는 사라진다")
    void givenReactionSuccess_whenCancelled_thenFirstSuccessPreserved() {
        reactions.toggle(sender, videoId, ReactionType.LIKE);
        var first = guidance.get(roomId, sender).firstReactedAt();
        assertThat(first).isNotNull();
        reactions.toggle(sender, videoId, ReactionType.LIKE);
        assertThat(guidance.get(roomId, sender).firstReactedAt()).isEqualTo(first);
        assertThat(guidance.get(roomId, sender).nextGuide()).isEqualTo(InteractionGuideKind.REPLY);
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "late-device")).reason())
                .isEqualTo("ACTION_ALREADY_COMPLETED");
    }

    @Test
    @DisplayName("영상 답장 삭제와 재시도 후에도 최초 성공 시각은 유지된다")
    void givenReplySuccess_whenDeletedAndRepliedAgain_thenFirstSuccessPreserved() {
        String requestId = UUID.randomUUID().toString();
        replies.send(roomId, videoId, sender, "어디인가요?", requestId);
        var first = guidance.get(roomId, sender).firstVideoRepliedAt();
        assertThat(first).isNotNull();
        jdbc.update("update chat_messages set admin_deleted_at=current_timestamp where chat_room_id=? and sender_id=?", chatId, sender);
        replies.send(roomId, videoId, sender, "어디인가요?", requestId);
        replies.send(roomId, videoId, sender, "좋네요", UUID.randomUUID().toString());
        assertThat(guidance.get(roomId, sender).firstVideoRepliedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("첫 행동 시각은 성공한 원래 행동과 함께 롤백된다")
    void givenReactionAndReply_whenTransactionRollsBack_thenNoFirstActionPersists() {
        var transaction = new TransactionTemplate(tx);
        assertThatThrownBy(() -> transaction.execute(status -> {
            reactions.toggle(sender, videoId, ReactionType.LIKE);
            replies.send(roomId, videoId, sender, "질문", UUID.randomUUID().toString());
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);
        var state = guidance.get(roomId, sender);
        assertThat(state.firstReactedAt()).isNull();
        assertThat(state.firstVideoRepliedAt()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from video_reactions where user_id=?", Long.class, sender)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where sender_id=?", Long.class, sender)).isZero();
    }

    @Test
    @DisplayName("다른 기기에서 먼저 완료한 행동은 다시 안내하지 않는다")
    void givenOtherDeviceCompleted_whenStaleClientClaims_thenDenied() {
        guidance.get(roomId, sender);
        reactions.toggle(sender, videoId, ReactionType.HEART);
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "stale")).claimed()).isFalse();
        assertThat(claims()).isZero();
    }

    @Test
    @DisplayName("반응과 답장을 다른 트랜잭션에서 동시에 성공해도 두 최초 시각이 보존된다")
    void givenTwoActions_whenSucceedConcurrently_thenBothTimestampsSurvive() throws Exception {
        race(2, i -> {
            if (i == 0) reactions.toggle(sender, videoId, ReactionType.HEART);
            else replies.send(roomId, videoId, sender, "질문", UUID.randomUUID().toString());
            return true;
        });
        var state = guidance.get(roomId, sender);
        assertThat(state.firstReactedAt()).isNotNull();
        assertThat(state.firstVideoRepliedAt()).isNotNull();
        assertThat(state.nextGuide()).isNull();
    }

    @Test
    @DisplayName("참가자가 아니면 조회·확보 모두 거절한다")
    void givenOutsider_whenGetOrClaim_thenForbidden() throws Exception {
        mvc.perform(get("/api/v1/rooms/{id}/interaction-guidance", roomId).with(authentication(auth(-5L))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/rooms/{id}/interaction-guidance/claim", roomId).with(authentication(auth(-5L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"REACTION\",\"video_post_id\":" + videoId + ",\"viewer_session_id\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("본인·숨김·삭제·미래 영상에는 안내를 소비하지 않는다")
    void givenUnavailableVideos_whenClaim_thenNoConsumption() {
        Long own = video(senderMemberId, now.minusHours(1));
        assertThat(guidance.claim(roomId, sender, new ClaimInteractionGuideCommand(InteractionGuideKind.REACTION, own, "own")).claimed()).isFalse();
        jdbc.update("update video_posts set is_hidden=true where id=?", videoId);
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "hidden")).claimed()).isFalse();
        jdbc.update("update video_posts set is_hidden=false, deleted_at=current_timestamp where id=?", videoId);
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "deleted")).claimed()).isFalse();
        Long future = video(ownerMemberId, now.plusHours(1));
        assertThat(guidance.claim(roomId, sender, new ClaimInteractionGuideCommand(InteractionGuideKind.REACTION, future, "future")).claimed()).isFalse();
        assertThat(claims()).isZero();
    }

    @Test
    @DisplayName("상대와 차단 관계이거나 그룹채팅을 나갔으면 안내하지 않는다")
    void givenBlockedOrLeftTarget_whenClaim_thenDenied() {
        jdbc.update("insert into user_blocks(blocker_user_id, blocked_user_id, created_at, updated_at) values (?,?,current_timestamp,current_timestamp)", sender, owner);
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "blocked")).claimed()).isFalse();
        jdbc.update("delete from user_blocks where blocker_user_id=?", sender);
        jdbc.update("update chat_room_members set left_at=current_timestamp where chat_room_id=? and user_id=?", chatId, owner);
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "left")).claimed()).isFalse();
        assertThat(claims()).isZero();
    }

    @Test
    @DisplayName("시작 1시간 이전과 최종선택 상태에는 안내하지 않는다")
    void givenBeforeThresholdOrSelecting_whenGetOrClaim_thenNone() {
        jdbc.update("update rooms set starts_at=? where id=?", now.minusMinutes(30), roomId);
        assertThat(guidance.get(roomId, sender).nextGuide()).isNull();
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "early")).claimed()).isFalse();
        jdbc.update("update rooms set starts_at=?,final_selection_opens_at=? where id=?", now.minusHours(2), now.minusMinutes(1), roomId);
        assertThat(guidance.get(roomId, sender).nextGuide()).isNull();
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "selecting")).claimed()).isFalse();
        assertThat(claims()).isZero();
    }

    @Test
    @DisplayName("등록 시작 시각보다 과거 방은 배정하거나 행동 상태를 저장하지 않는다")
    void givenLegacyRoom_whenInteract_thenNotEnrolled() {
        jdbc.update("update rooms set starts_at=? where id=?", LocalDateTime.of(2025, 12, 31, 12, 0), roomId);
        reactions.toggle(sender, videoId, ReactionType.LIKE);
        assertThat(guidance.get(roomId, sender).variant()).isEqualTo(InteractionExperimentVariant.NOT_ENROLLED);
        assertThat(jdbc.queryForObject("select count(*) from room_interaction_first_actions where room_member_id=?", Long.class, senderMemberId)).isZero();
    }

    @Test
    @DisplayName("대조군 배정은 방 전체에 고정되고 행동은 동일하게 기록하지만 안내하지 않는다")
    void givenPersistedControlAssignment_whenGetAndReact_thenNoGuideButActionRecorded() {
        jdbc.update("insert into room_interaction_experiments(room_id,experiment_key,variant,assigned_at) values (?,?,?,?)",
                roomId, "frozen-test", "CONTROL", now);
        assertThat(guidance.get(roomId, sender).variant()).isEqualTo(InteractionExperimentVariant.CONTROL);
        assertThat(guidance.get(roomId, owner).experimentKey()).isEqualTo("frozen-test");
        assertThat(guidance.get(roomId, sender).nextGuide()).isNull();
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "control")).claimed()).isFalse();
        reactions.toggle(sender, videoId, ReactionType.LIKE);
        assertThat(guidance.get(roomId, sender).firstReactedAt()).isNotNull();
        assertThat(claims()).isZero();
    }

    @Test
    @DisplayName("앱에 방문하지 않아도 기존 방 시작 이벤트에서 배정을 한 번 보관한다")
    void givenNoAppVisits_whenRoomStartedEventCommits_thenAssignmentPersisted() {
        new TransactionTemplate(tx).executeWithoutResult(status -> events.publishEvent(new RoomStartedEvent(roomId)));
        assertThat(jdbc.queryForObject("select count(*) from room_interaction_experiments where room_id=?", Long.class, roomId)).isOne();
        var assignment = jdbc.queryForMap("select * from room_interaction_experiments where room_id=?", roomId);
        new TransactionTemplate(tx).executeWithoutResult(status -> events.publishEvent(new RoomStartedEvent(roomId)));
        assertThat(jdbc.queryForMap("select * from room_interaction_experiments where room_id=?", roomId)).isEqualTo(assignment);
        assertThat(claims()).isZero();
    }

    @Test
    @DisplayName("1시간 이후에도 ACTIVE인 다음 날 영상에는 임의의 24시간 마감을 적용하지 않는다")
    void givenSecondDayActiveRoom_whenClaim_thenAllowed() {
        jdbc.update("update rooms set starts_at=? where id=?", now.minusHours(25), roomId);
        assertThat(guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "second-day")).claimed()).isTrue();
    }

    @Test
    @DisplayName("확보 요청을 재시도하면서 영상이나 종류를 바꾸면 원래 확보를 재사용하지 않는다")
    void givenClaimedRequest_whenPayloadChanged_thenDenied() {
        guidance.claim(roomId, sender, command(InteractionGuideKind.REACTION, "same-session"));
        Long anotherVideo = video(ownerMemberId, now.minusHours(1));
        var mismatch = guidance.claim(roomId, sender,
                new ClaimInteractionGuideCommand(InteractionGuideKind.REACTION, anotherVideo, "same-session"));
        assertThat(mismatch.reason()).isEqualTo("REQUEST_MISMATCH");
        assertThat(claims()).isOne();
    }

    @Test
    @DisplayName("claim API는 snake_case와 입력 제약을 검증한다")
    void givenClaimJson_whenPost_thenSerializesAndValidates() throws Exception {
        mvc.perform(post("/api/v1/rooms/{id}/interaction-guidance/claim", roomId).with(authentication(auth(sender)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"REACTION\",\"video_post_id\":" + videoId + ",\"viewer_session_id\":\"http-viewer\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.claimed").value(true))
                .andExpect(jsonPath("$.data.claim_id").isString()).andExpect(jsonPath("$.data.claimed_at").exists());
        mvc.perform(post("/api/v1/rooms/{id}/interaction-guidance/claim", roomId).with(authentication(auth(sender)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"REACTION\",\"video_post_id\":0,\"viewer_session_id\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    private <T> List<T> race(int count, java.util.function.IntFunction<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<T>>();
            for (int i = 0; i < count; i++) {
                final int index = i;
                futures.add(executor.submit(() -> { ready.countDown(); start.await(); return action.apply(index); }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = new ArrayList<T>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally { executor.shutdownNow(); }
    }
}
