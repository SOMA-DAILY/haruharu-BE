package org.son.haruharu.admin.domain.mission.common.application;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminRoomMissionApiIntegrationTest {
    @Autowired MockMvc mvc;
    @PersistenceContext EntityManager em;
    final LocalDateTime start = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0);
    static UsernamePasswordAuthenticationToken role(String role) {
        return new UsernamePasswordAuthenticationToken(4L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    String roomBody(String missions) {
        return """
          {"starts_at":"%s","final_selection_opens_at":"%s","final_selection_closes_at":"%s",
           "result_at":"%s","ends_at":"%s","total_days":3,"max_uses":6,"missions":%s}
          """.formatted(start,start.plusHours(60),start.plusHours(63),start.plusHours(64),start.plusHours(72),missions);
    }
    long room(String missions) throws Exception {
        var body=mvc.perform(post("/api/v1/admin/rooms").with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(roomBody(missions)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.data.room_id", Long.class);
    }
    String base(long room) { return "/api/v1/admin/rooms/"+room+"/missions"; }
    String mission(LocalDateTime at, String announcement) {
        return """
          {"type":"ANNOUNCEMENT","details":{"content":"미션 본문","reveal_fields":["AGE","MBTI","AGE"]},
           "opens_at":"%s","announcement":%s}
          """.formatted(at,announcement);
    }
    String announcement(LocalDateTime at, String content) {
        return """
          {"announcement_type":"START","run_at":"%s","content":"%s","push":true,
           "push_title":"📢 하루 공지","push_body":"내용 확인"}
          """.formatted(at,content);
    }
    long create(long room, LocalDateTime at, String announcement) throws Exception {
        var body=mvc.perform(post(base(room)).with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(mission(at,announcement)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.data.mission_id",Long.class);
    }
    long announcementId(long room,long mission) throws Exception {
        var body=mvc.perform(get(base(room)+"/"+mission+"/announcements").with(authentication(role("CS"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.data.announcements[0].announcement_id",Long.class);
    }

    @Test
    void givenInitialMissions_whenCreateRoom_thenSavesAllAndKeepsOldApiRetired() throws Exception {
        var at=start.minusHours(2);
        var first=mission(at,announcement(at,"첫 안내"));
        long room=room("["+String.join(",",List.of(first,first,first,first,first))+"]");
        mvc.perform(get(base(room)).with(authentication(role("CS"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.missions.length()").value(5))
                .andExpect(jsonPath("$.data.missions[0].opens_at").value(at.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.data.missions[0].details.reveal_fields[0]").value("MBTI"))
                .andExpect(jsonPath("$.data.missions[0].details.reveal_fields[1]").value("AGE"));
        assertThat(em.createQuery("select count(a) from MissionAnnouncementEntity a",Long.class).getSingleResult()).isEqualTo(5);
        mvc.perform(get("/api/v1/admin/rooms/"+room+"/schedule").with(authentication(role("OPERATOR"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void givenInvalidInitialMissions_whenCreateRoom_thenFieldErrorsAndRollback() throws Exception {
        mvc.perform(post("/api/v1/admin/rooms").with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("""
                 [{"type":"ANNOUNCEMENT","details":{"content":"a"}},{"type":"ANNOUNCEMENT","details":{},"opens_at":"%s"}]
                """.formatted(start))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("missions[0].opens_at"))
                .andExpect(jsonPath("$.error.fields[1].field").value("missions[1].details.content"));
        // 실제 트랜잭션 롤백은 별도 AdminRoomCreationRollbackIntegrationTest에서 검증한다.
        assertThat(em.createQuery("select count(a) from MissionAnnouncementEntity a",Long.class).getSingleResult()).isZero();
    }

    @Test
    void givenInvalidRevealFieldsInMultipleMissions_whenCreatingRoom_thenReportsIndexedErrors() throws Exception {
        mvc.perform(post("/api/v1/admin/rooms").with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("""
                [{"type":"ANNOUNCEMENT","details":{"reveal_fields":["INVALID"]},"opens_at":"%s"},
                 {"type":"ANNOUNCEMENT","details":{"reveal_fields":[null]},"opens_at":"%s"}]
                """.formatted(start, start))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("missions[0].details.reveal_fields"))
                .andExpect(jsonPath("$.error.fields[1].field").value("missions[1].details.reveal_fields"));
    }

    @Test
    void givenDetailsBeforeType_whenCreatingMission_thenUsesAllowlistedTypeAndNestedResponse() throws Exception {
        long room = room("[]");
        mvc.perform(post(base(room)).with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"details":{"content":"선행 상세","reveal_fields":[]},"opens_at":"%s","type":"ANNOUNCEMENT"}
                """.formatted(start)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.details.content").value("선행 상세"))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.reveal_fields").doesNotExist());
        for (String body : List.of(
                "{\"type\":\"ANNOUNCEMENT\",\"details\":null,\"opens_at\":\"" + start + "\"}",
                "{\"type\":\"ANNOUNCEMENT\",\"opens_at\":\"" + start + "\"}",
                "{\"details\":{\"content\":\"누락\"}}",
                "{\"type\":\"java.lang.Runtime\",\"details\":{\"content\":\"금지\"}}")) {
            mvc.perform(post(base(room)).with(authentication(role("OPERATOR")))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void givenCs_whenMutatingMissionOrAnnouncement_thenForbidden() throws Exception {
        long room=room("[]"); long mission=create(room,start,"null");
        for (var request:List.of(post(base(room)),put(base(room)+"/"+mission),post(base(room)+"/"+mission+"/cancel"),
                post(base(room)+"/"+mission+"/announcements"),put(base(room)+"/"+mission+"/announcements/1"),
                post(base(room)+"/"+mission+"/announcements/1/send-now"),
                delete(base(room)+"/"+mission+"/announcements/1/message"))) {
            mvc.perform(request.with(authentication(role("CS"))).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"run_at", "content", "push_title", "push_body", "announcement_type"})
    void givenInvalidAnnouncement_whenCreatingMission_thenReportsFullInputPath(String field) throws Exception {
        long room = room("[]");
        long existingMission = create(room, start, "null");
        String invalid = switch (field) {
            case "run_at" -> announcement(start.minusSeconds(1), "정상 본문");
            case "content" -> announcement(start, "가".repeat(501));
            case "push_title" -> announcement(start, "정상 본문").replace("📢 하루 공지", "가".repeat(16));
            case "push_body" -> announcement(start, "정상 본문").replace("내용 확인", "가".repeat(31));
            case "announcement_type" -> announcement(start, "정상 본문").replace("\"START\"", "null");
            default -> throw new IllegalArgumentException(field);
        };
        mvc.perform(post(base(room)).with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(mission(start, invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("announcement." + field));
        mvc.perform(post("/api/v1/admin/rooms").with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("[" + mission(start, invalid) + "]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("missions[0].announcement." + field));
        mvc.perform(post(base(room) + "/" + existingMission + "/announcements").with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value(field));
    }

    @Test
    void givenMissionWithoutChat_whenAddingAndCancelingAnnouncements_thenIndependentLifecycle() throws Exception {
        long room=room("[]"); long mission=create(room,start,"null"); String path=base(room)+"/"+mission;
        mvc.perform(get(path+"/announcements").with(authentication(role("CS"))))
                .andExpect(jsonPath("$.data.announcements").isEmpty());
        mvc.perform(post(path+"/announcements").with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(announcement(start,"추가 안내")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.created_by").value("admin:4"));
        long id=announcementId(room,mission);
        mvc.perform(post(path+"/announcements/"+id+"/cancel").with(authentication(role("OPERATOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"));
        mvc.perform(get(path).with(authentication(role("CS"))))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        mvc.perform(put(path+"/announcements/"+id).with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(announcement(start,"바꿈")))
                .andExpect(status().isConflict());
    }

    @Test
    void givenPendingMission_whenReplaceDefinition_thenClearDeadlineAndRejectPrematureAnnouncement() throws Exception {
        long room=room("[]"); long mission=create(room,start,"null"); String path=base(room)+"/"+mission;
        mvc.perform(put(path).with(authentication(role("OPERATOR"))).contentType(MediaType.APPLICATION_JSON)
                .content(mission(start,"null")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.closes_at").isEmpty())
                .andExpect(jsonPath("$.data.effective_closes_at").value(start.plusHours(72).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        mvc.perform(post(path+"/announcements").with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(announcement(start.minusSeconds(1),"너무 빠름")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path+"/announcements").with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(announcement(start,"정상")))
                .andExpect(status().isCreated());
        long id=announcementId(room,mission);
        mvc.perform(post(path+"/announcements/"+id+"/send-now").with(authentication(role("OPERATOR"))))
                .andExpect(status().isConflict());
        mvc.perform(put(path+"/announcements/"+id).with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(announcement(start,"바꾼 본문").replace("📢 하루 공지","가".repeat(16))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("push_title"));
    }

    @Test
    void givenSentAnnouncement_whenDeleteTwice_thenKeepMissionAndMarkMessage() throws Exception {
        long room=room("[]"); long mission=create(room,start,announcement(start,"원문"));
        long id=announcementId(room,mission); String path=base(room)+"/"+mission+"/announcements/"+id;
        mvc.perform(delete(path+"/message").with(authentication(role("OPERATOR")))).andExpect(status().isConflict());
        var message=ChatMessageEntity.from(ChatMessage.mission(100L,"원문",mission,id));
        em.persist(message);em.flush();
        em.createQuery("update MissionAnnouncementEntity a set a.status=org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus.SENT, a.sentMessageId=:message where a.id=:id")
                .setParameter("message",message.toDomain().id()).setParameter("id",id).executeUpdate();
        em.clear();
        for(int i=0;i<2;i++) mvc.perform(delete(path+"/message").with(authentication(role("OPERATOR"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.message_deleted_at").isNotEmpty());
        mvc.perform(get(base(room)+"/"+mission).with(authentication(role("CS"))))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    @Test
    void givenRetiredSchedulePayload_whenCreateRoom_thenRejectInsteadOfSilentlyDroppingMissions() throws Exception {
        mvc.perform(post("/api/v1/admin/rooms").with(authentication(role("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content(roomBody("[]").replace("\"missions\":[]", "\"chat_schedule\":[{\"message_content\":\"유실 금지\"}]")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("chat_schedule은 폐기되었습니다. missions를 사용해 주세요"));
    }
}
