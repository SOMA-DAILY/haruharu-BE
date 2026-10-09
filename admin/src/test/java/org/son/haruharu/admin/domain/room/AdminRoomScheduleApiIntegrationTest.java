package org.son.haruharu.admin.domain.room;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.storage.ChatMessageEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 방 편성 어드민 API(#457). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminRoomScheduleApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @PersistenceContext
    EntityManager em;

    // 방 시작 10:00. 첫 줄은 시작 전 08:00(서버가 거절하면 안 됨).
    private final LocalDateTime startsAt = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);

    private static UsernamePasswordAuthenticationToken operator() {
        return new UsernamePasswordAuthenticationToken(4L, null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
    }

    private static UsernamePasswordAuthenticationToken cs() {
        return new UsernamePasswordAuthenticationToken(5L, null, List.of(new SimpleGrantedAuthority("ROLE_CS")));
    }

    private String day(int day, int hour) {
        return startsAt.toLocalDate().plusDays(day - 1L).atTime(hour, 0).toString() + ":00";
    }

    private String createRoomBody(String chatSchedule) {
        return """
                {"starts_at":"%s","final_selection_opens_at":"%s","final_selection_closes_at":"%s",
                 "result_at":"%s","ends_at":"%s","total_days":3,"max_uses":6,
                 "chat_schedule":%s}
                """.formatted(startsAt, startsAt.plusHours(60), startsAt.plusHours(63), startsAt.plusHours(64),
                startsAt.plusHours(72), chatSchedule);
    }

    private Long createRoom(String chatSchedule) throws Exception {
        String res = mockMvc.perform(post("/api/v1/admin/rooms").with(authentication(operator()))
                        .contentType(MediaType.APPLICATION_JSON).content(createRoomBody(chatSchedule)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(res).read("$.data.room_id", Long.class);
    }

    private String defaultFive() {
        return """
                [
                 {"run_at":"%s","reveal_fields":[],"message_content":"하루방이 열렸어요 ☁️","push":false,"push_title":null,"push_body":null},
                 {"run_at":"%s","reveal_fields":["MBTI","INTO_NOW"],"message_content":"서로의 MBTI 가 공개됐어요 👀","push":false},
                 {"run_at":"%s","reveal_fields":["AGE"],"message_content":"서로의 나이가 공개됐어요 😊","push":false},
                 {"run_at":"%s","reveal_fields":["DEPARTMENT","AGE"],"message_content":"서로의 학과가 공개됐어요 💙","push":false},
                 {"run_at":"%s","reveal_fields":[],"message_content":"2시간 뒤면 벌써 최종 선택이에요 ⏰","push":true,"push_title":"📢 하루 공지","push_body":"최종 선택까지 2시간 남았어요"}
                ]
                """.formatted(day(1, 8), day(1, 18), day(2, 12), day(2, 18), day(3, 16));
    }

    @Test
    @DisplayName("방 생성 때 보낸 5줄이 그대로 예약된다(시작 전 08:00 줄, 같은 공개 항목 중복 행도 허용)")
    void createRoomWithSchedule_savesAllRowsAsIs() throws Exception {
        Long roomId = createRoom(defaultFive());

        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}/schedule", roomId).with(authentication(cs())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].run_at").value(day(1, 8)))
                .andExpect(jsonPath("$.data[0].day").value(1))
                .andExpect(jsonPath("$.data[0].reveal_fields").isEmpty())
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[0].created_by").value("admin:4"))
                .andExpect(jsonPath("$.data[1].reveal_fields[0]").value("MBTI"))
                .andExpect(jsonPath("$.data[1].reveal_fields[1]").value("INTO_NOW"))
                .andExpect(jsonPath("$.data[3].reveal_fields[0]").value("AGE"))
                .andExpect(jsonPath("$.data[3].reveal_fields[1]").value("DEPARTMENT"))
                .andExpect(jsonPath("$.data[4].day").value(3))
                .andExpect(jsonPath("$.data[4].push").value(true))
                .andExpect(jsonPath("$.data[4].push_title").value("📢 하루 공지"))
                .andExpect(jsonPath("$.data[4].message_content").value("2시간 뒤면 벌써 최종 선택이에요 ⏰"));

        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}", roomId).with(authentication(cs())))
                .andExpect(jsonPath("$.data.starts_at").value(startsAt.toString() + ":00"))
                .andExpect(jsonPath("$.data.final_selection_opens_at").isString())
                .andExpect(jsonPath("$.data.ends_at").isString());
    }

    @Test
    @DisplayName("편성 줄 검증 실패면 칸별 error.fields(chat_schedule[i].칸) 가 내려간다(같은 트랜잭션이라 방도 롤백)")
    void createRoomWithInvalidSchedule_rollsBackWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rooms").with(authentication(operator()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRoomBody("""
                                [{"run_at":null,"reveal_fields":[],"message_content":"a"},
                                 {"run_at":"%s","reveal_fields":[],"message_content":null}]""".formatted(day(1, 9)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("chat_schedule[0].run_at"))
                .andExpect(jsonPath("$.error.fields[1].field").value("chat_schedule[1].message_content"))
                .andExpect(jsonPath("$.error.fields[1].message").value("운영 글이나 공개 항목 중 하나는 있어야 해요"));
    }

    @Test
    @DisplayName("행 추가: run_at null 이면 지금, 푸시 제목 16자는 400(이모지는 한 글자), CS 는 403")
    void addRow() throws Exception {
        Long roomId = createRoom("[]");
        String base = "/api/v1/admin/rooms/" + roomId + "/schedule";

        mockMvc.perform(post(base).with(authentication(operator())).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"run_at":null,"reveal_fields":[],"message_content":"조용하네요","push":true,
                                 "push_title":"📢 하루 공지","push_body":null}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.run_at").isString())
                .andExpect(jsonPath("$.data.day").isNumber());

        mockMvc.perform(post(base).with(authentication(operator())).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"run_at":null,"reveal_fields":["MBTI"],"push":true,"push_title":"가나다라마바사아자차카타파하가나"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("push_title"))
                .andExpect(jsonPath("$.error.fields[0].message").value("15자까지 쓸 수 있어요"));

        mockMvc.perform(post(base).with(authentication(cs())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message_content\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH 직후 send-now 는 둘 다 통과하고, 취소한 행은 409 SCHEDULE_ITEM_NOT_EDITABLE")
    void patchThenSendNowThenCancel() throws Exception {
        Long roomId = createRoom(defaultFive());
        String list = mockMvc.perform(get("/api/v1/admin/rooms/{roomId}/schedule", roomId).with(authentication(operator())))
                .andReturn().getResponse().getContentAsString();
        Long itemId = JsonPath.parse(list).read("$.data[1].id", Long.class);
        String item = "/api/v1/admin/rooms/" + roomId + "/schedule/" + itemId;

        mockMvc.perform(patch(item).with(authentication(operator())).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"run_at":"%s","message_content":"고친 문구","push":true,"push_title":null,"push_body":null}"""
                                .formatted(day(1, 19))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run_at").value(day(1, 19)))
                .andExpect(jsonPath("$.data.message_content").value("고친 문구"))
                .andExpect(jsonPath("$.data.reveal_fields[0]").value("MBTI"))
                .andExpect(jsonPath("$.data.push").value(true));
        mockMvc.perform(post(item + "/send-now").with(authentication(operator())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        mockMvc.perform(post(item + "/cancel").with(authentication(operator())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
        mockMvc.perform(patch(item).with(authentication(operator())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message_content\":\"또\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_ITEM_NOT_EDITABLE"))
                .andExpect(jsonPath("$.error.message").value("이미 보냈거나 취소된 편성이에요"));
        mockMvc.perform(post(item + "/send-now").with(authentication(operator())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH: null 칸은 기존 값 유지, 빈 문자열은 지움, 합친 결과로 검증, SCHEDULED 아니면 검증보다 409 먼저")
    void patchKeepsNullAndClearsEmpty() throws Exception {
        Long roomId = createRoom(defaultFive());
        String list = mockMvc.perform(get("/api/v1/admin/rooms/{roomId}/schedule", roomId).with(authentication(operator())))
                .andReturn().getResponse().getContentAsString();
        Long revealRow = JsonPath.parse(list).read("$.data[1].id", Long.class);   // MBTI+INTO_NOW + 글
        Long textOnlyRow = JsonPath.parse(list).read("$.data[4].id", Long.class); // 글 + 푸시
        String base = "/api/v1/admin/rooms/" + roomId + "/schedule/";

        mockMvc.perform(patch(base + textOnlyRow).with(authentication(operator())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"run_at\":\"%s\"}".formatted(day(3, 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run_at").value(day(3, 15)))
                .andExpect(jsonPath("$.data.message_content").value("2시간 뒤면 벌써 최종 선택이에요 ⏰"))
                .andExpect(jsonPath("$.data.push").value(true))
                .andExpect(jsonPath("$.data.push_title").value("📢 하루 공지"));

        mockMvc.perform(patch(base + revealRow).with(authentication(operator())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message_content\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message_content").isEmpty())
                .andExpect(jsonPath("$.data.reveal_fields[0]").value("MBTI"));

        mockMvc.perform(patch(base + textOnlyRow).with(authentication(operator())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message_content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("message_content"));

        mockMvc.perform(post(base + textOnlyRow + "/cancel").with(authentication(operator()))).andExpect(status().isOk());
        mockMvc.perform(patch(base + textOnlyRow).with(authentication(operator())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message_content\":\"\",\"push_title\":\"가나다라마바사아자차카타파하가나\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_ITEM_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("보낸 운영 글 삭제: 보내기 전엔 409, SENT 면 admin_deleted_at 을 찍고 두 번째도 200")
    void deleteSentMessage() throws Exception {
        Long roomId = createRoom(defaultFive());
        String list = mockMvc.perform(get("/api/v1/admin/rooms/{roomId}/schedule", roomId).with(authentication(operator())))
                .andReturn().getResponse().getContentAsString();
        Long itemId = JsonPath.parse(list).read("$.data[0].id", Long.class);
        String url = "/api/v1/admin/rooms/" + roomId + "/schedule/" + itemId + "/message";

        mockMvc.perform(delete(url).with(authentication(operator()))).andExpect(status().isConflict());

        ChatMessageEntity message = ChatMessageEntity.from(ChatMessage.system(77L, "하루방이 열렸어요", itemId));
        em.persist(message);
        Long messageId = message.toDomain().id();
        em.createQuery("update RoomScheduleItemEntity i set i.status = org.son.haruharu.domain.roomschedule.implement"
                        + ".RoomScheduleStatus.SENT, i.sentMessageId = :m where i.id = :id")
                .setParameter("m", messageId).setParameter("id", itemId).executeUpdate();
        em.flush();
        em.clear();

        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}/schedule", roomId).with(authentication(operator())))
                .andExpect(jsonPath("$.data[0].sent_message_id").value(messageId))
                .andExpect(jsonPath("$.data[0].message_deleted_at").isEmpty());
        mockMvc.perform(delete(url).with(authentication(operator())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message_deleted_at").isString());
        mockMvc.perform(delete(url).with(authentication(operator()))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}/schedule", roomId).with(authentication(operator())))
                .andExpect(jsonPath("$.data[0].message_deleted_at").isString())
                .andExpect(jsonPath("$.data[1].message_deleted_at").isEmpty());
        LocalDateTime deletedAt = em.createQuery(
                        "select m.adminDeletedAt from ChatMessageEntity m where m.id = :id", LocalDateTime.class)
                .setParameter("id", messageId).getSingleResult();
        assertThat(deletedAt).isNotNull();
        mockMvc.perform(delete(url).with(authentication(cs()))).andExpect(status().isForbidden());
    }
}
