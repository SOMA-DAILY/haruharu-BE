package org.son.haruharu.domain.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Real PostgreSQL transactions exercise the room lock across independent HTTP requests. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuestionApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add(
                "spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserJpaRepository users;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired Clock clock;
    Long roomId;
    Long sender;
    Long outsider;
    List<Long> recipients;
    List<Long> recipientMembers;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now(clock);
        roomId =
                rooms.save(
                                RoomEntity.from(
                                        Room.createScheduled(
                                                now.minusHours(25),
                                                now.plusHours(23),
                                                now.plusHours(25),
                                                now.plusHours(26),
                                                now.plusHours(47),
                                                3)))
                        .toDomain()
                        .id();
        sender = user();
        outsider = user();
        member(sender, "MALE", "숨겨진발신자", now);
        recipients = new ArrayList<>();
        recipientMembers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Long recipient = user();
            recipients.add(recipient);
            recipientMembers.add(member(recipient, "FEMALE", "수신자" + i, now));
        }
    }

    @Test
    @DisplayName("익명 한국어 질문은 참여자만 읽고 상세를 열기 전까지 미확인으로 남는다")
    void givenKoreanQuestion_whenReadAsRecipient_thenContentSurvivesAndSenderStaysPrivate()
            throws Exception {
        create(0, UUID.randomUUID()).andExpect(status().isOk());
        Long questionId = questionId();
        mvc.perform(get(base())).andExpect(status().isUnauthorized());
        mvc.perform(get(base() + "/" + questionId).with(authentication(auth(outsider))))
                .andExpect(status().isForbidden());
        MvcResult detail =
                mvc.perform(
                                get(base() + "/" + questionId)
                                        .with(authentication(auth(recipients.getFirst()))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.question").value("좋아하는 계절은? 🌸"))
                        .andExpect(jsonPath("$.data.direction").value("RECEIVED"))
                        .andExpect(jsonPath("$.data.recipient").doesNotExist())
                        .andReturn();
        assertThat(detail.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("sender_user_id", "sender_member_id", "숨겨진발신자");
        mvc.perform(get(base()).with(authentication(auth(recipients.getFirst()))))
                .andExpect(jsonPath("$.data.received[0].unread").value(true));
        send(recipients.getFirst(), "/" + questionId + "/read", "").andExpect(status().isOk());
        mvc.perform(get(base()).with(authentication(auth(recipients.getFirst()))))
                .andExpect(jsonPath("$.data.received[0].unread").value(false));
        send(sender, "/" + questionId + "/answer", "{\"content\":\"봄\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("동일 질문의 동시 재시도는 질문권을 한 번만 사용한다")
    void givenConcurrentRetries_whenCreate_thenOneQuestionAndOneCreditConsumed() throws Exception {
        UUID requestId = UUID.randomUUID();
        List<Integer> statuses =
                concurrently(8, i -> create(0, requestId).andReturn().getResponse().getStatus());
        assertThat(statuses).containsOnly(200);
        assertThat(countQuestions()).isEqualTo(1);
        mvc.perform(get(base()).with(authentication(auth(sender))))
                .andExpect(jsonPath("$.data.remaining_count").value(2));
    }

    @Test
    @DisplayName("서로 다른 세 상대에게 동시 질문하면 모두 성공하고 전체 기록이 조회된다")
    void givenThreeConcurrentTargets_whenCreate_thenAllSucceedAndRemainVisible() throws Exception {
        List<Integer> statuses =
                concurrently(
                        3, i -> create(i, UUID.randomUUID()).andReturn().getResponse().getStatus());
        assertThat(statuses).containsOnly(200);
        assertThat(countQuestions()).isEqualTo(3);
        mvc.perform(get(base()).with(authentication(auth(sender))))
                .andExpect(jsonPath("$.data.remaining_count").value(0))
                .andExpect(jsonPath("$.data.targets.length()").value(3))
                .andExpect(jsonPath("$.data.targets[0].question_id").isNumber())
                .andExpect(jsonPath("$.data.targets[1].question_id").isNumber())
                .andExpect(jsonPath("$.data.targets[2].question_id").isNumber());
    }

    @Test
    @DisplayName("같은 상대에게 서로 다른 요청으로 동시에 질문해도 한 건만 저장된다")
    void givenSameTargetConcurrentRequests_whenCreate_thenOnlyOneQuestionPersists()
            throws Exception {
        List<Integer> statuses =
                concurrently(4, i -> create(0, UUID.randomUUID()).andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409, 409, 409);
        assertThat(countQuestions()).isEqualTo(1);
        mvc.perform(get(base()).with(authentication(auth(sender))))
                .andExpect(jsonPath("$.data.remaining_count").value(2));
    }

    @Test
    @DisplayName("서로 다른 동시 답변 중 하나만 저장되고 답변 읽음은 명시적으로 갱신된다")
    void givenConcurrentAnswers_whenAnswer_thenExactlyOneAnswerPersists() throws Exception {
        create(0, UUID.randomUUID()).andExpect(status().isOk());
        Long id = questionId();
        List<Integer> statuses =
                concurrently(
                        2,
                        i ->
                                send(
                                                recipients.getFirst(),
                                                "/" + id + "/answer",
                                                "{\"content\":\"봄" + i + "\"}")
                                        .andReturn()
                                        .getResponse()
                                        .getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        String saved =
                jdbc.queryForObject(
                        "select answer from anonymous_questions where id = ?", String.class, id);
        assertThat(saved).isIn("봄0", "봄1");
        mvc.perform(get(base()).with(authentication(auth(sender))))
                .andExpect(jsonPath("$.data.latest_event.type").value("QUESTION_ANSWERED"));
        send(sender, "/" + id + "/read", "").andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select sender_read from anonymous_questions where id = ?",
                                Boolean.class,
                                id))
                .isTrue();
    }

    @Test
    @DisplayName("동시 신고는 한 번 접수되고 신고자에게만 숨기며 상대의 추가 답변을 막는다")
    void givenConcurrentReports_whenReport_thenOneReportAndOneSidedVisibility() throws Exception {
        create(0, UUID.randomUUID()).andExpect(status().isOk());
        Long id = questionId();
        List<Integer> statuses =
                concurrently(
                        6,
                        i ->
                                send(
                                                recipients.getFirst(),
                                                "/" + id + "/report",
                                                "{\"reason\":\"OTHER\",\"detail\":\"불쾌한 질문\"}")
                                        .andReturn()
                                        .getResponse()
                                        .getStatus());
        assertThat(statuses).containsOnly(200);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from reports where reporter_user_id = ? and"
                                    + " reported_user_id = ?",
                                Long.class,
                                recipients.getFirst(),
                                sender))
                .isEqualTo(1);
        mvc.perform(get(base()).with(authentication(auth(recipients.getFirst()))))
                .andExpect(jsonPath("$.data.received").isEmpty());
        mvc.perform(get(base() + "/" + id).with(authentication(auth(sender))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        send(recipients.getFirst(), "/" + id + "/answer", "{\"content\":\"봄\"}")
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("익명 신고 후 일반 사용자 신고 결과로 질문자 신원을 추측할 수 없다")
    void givenAnonymousReport_whenReportSamePersonByUser_thenGenericReportStillSucceeds()
            throws Exception {
        create(0, UUID.randomUUID()).andExpect(status().isOk());
        send(recipients.getFirst(), "/" + questionId() + "/report", "{\"reason\":\"OTHER\"}")
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/reports")
                                .with(authentication(auth(recipients.getFirst())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"reported_user_id\":"
                                                + sender
                                                + ",\"reason\":\"OTHER\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("받은 질문을 커서로 넘겨도 전체 개수와 미확인 소식은 유지된다")
    void givenTwoIncomingQuestions_whenPageWithSizeOne_thenDistinctPagesAndTotalCount()
            throws Exception {
        create(0, UUID.randomUUID()).andExpect(status().isOk());
        Long secondSender = user();
        member(secondSender, "MALE", "다른발신자", LocalDateTime.now(clock));
        send(
                        secondSender,
                        "",
                        "{\"recipient_member_id\":"
                                + recipientMembers.getFirst()
                                + ",\"content\":\"좋아하는 색은?\",\"request_id\":\""
                                + UUID.randomUUID()
                                + "\"}")
                .andExpect(status().isOk());
        List<Long> ids =
                jdbc.queryForList(
                        "select id from anonymous_questions where room_id = ? order by id desc",
                        Long.class,
                        roomId);
        mvc.perform(
                        get(base())
                                .param("size", "1")
                                .with(authentication(auth(recipients.getFirst()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.received.length()").value(1))
                .andExpect(jsonPath("$.data.received[0].id").value(ids.getFirst()))
                .andExpect(jsonPath("$.data.received_count").value(2))
                .andExpect(jsonPath("$.data.received_next_cursor").value(ids.getFirst()));
        mvc.perform(
                        get(base())
                                .param("size", "1")
                                .param("cursor", ids.getFirst().toString())
                                .with(authentication(auth(recipients.getFirst()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.received.length()").value(1))
                .andExpect(jsonPath("$.data.received[0].id").value(ids.getLast()))
                .andExpect(jsonPath("$.data.received_count").value(2))
                .andExpect(jsonPath("$.data.received_next_cursor").doesNotExist())
                .andExpect(jsonPath("$.data.has_unread").value(true));
    }

    @Test
    @DisplayName("종료와 취소 방의 질문 기록은 참여자에게만 표시되고 나간 방은 제외된다")
    void givenClosedRoom_whenListHistory_thenOnlyCurrentParticipantsSeeReadableHistory()
            throws Exception {
        create(0, UUID.randomUUID()).andExpect(status().isOk());
        String history = "/api/v1/questions/rooms";
        jdbc.update(
                "update rooms set ends_at = ? where id = ?",
                LocalDateTime.now(clock).minusMinutes(1),
                roomId);
        mvc.perform(get(history).with(authentication(auth(sender))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms[0].room_id").value(roomId))
                .andExpect(jsonPath("$.data.rooms[0].status").value("ENDED"));
        mvc.perform(get(history).with(authentication(auth(outsider))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms").isEmpty());
        jdbc.update("update rooms set status = 'CANCELLED' where id = ?", roomId);
        mvc.perform(get(history).with(authentication(auth(recipients.getFirst()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms[0].room_id").value(roomId))
                .andExpect(jsonPath("$.data.rooms[0].status").value("CANCELLED"));
        jdbc.update(
                "update room_members set deleted_at = ? where room_id = ? and user_id = ?",
                LocalDateTime.now(clock),
                roomId,
                recipients.getFirst());
        mvc.perform(get(history).with(authentication(auth(recipients.getFirst()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms").isEmpty());
    }

    private Long user() {
        return users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    private Long member(Long userId, String gender, String nickname, LocalDateTime now) {
        return members.save(
                        RoomMemberEntity.from(
                                new RoomMember(null, roomId, userId, gender, nickname, null, now)))
                .toDomain()
                .id();
    }

    private String base() {
        return "/api/v1/rooms/" + roomId + "/questions";
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private org.springframework.test.web.servlet.ResultActions create(
            int recipientIndex, UUID requestId) throws Exception {
        return send(
                sender,
                "",
                "{\"recipient_member_id\":"
                        + recipientMembers.get(recipientIndex)
                        + ",\"content\":\"좋아하는 계절은? 🌸\",\"request_id\":\""
                        + requestId
                        + "\"}");
    }

    private org.springframework.test.web.servlet.ResultActions send(
            Long userId, String suffix, String body) throws Exception {
        return mvc.perform(
                post(base() + suffix)
                        .with(authentication(auth(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(body));
    }

    private Long questionId() {
        return jdbc.queryForObject(
                "select id from anonymous_questions where room_id = ?", Long.class, roomId);
    }

    private Long countQuestions() {
        return jdbc.queryForObject(
                "select count(*) from anonymous_questions where room_id = ?", Long.class, roomId);
    }

    private List<Integer> concurrently(int count, Request operation) throws Exception {
        var pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    if (!start.await(20, TimeUnit.SECONDS))
                                        throw new AssertionError("동시 시작 시간 초과");
                                    return operation.run(index);
                                }));
            }
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface Request {
        int run(int index) throws Exception;
    }
}
