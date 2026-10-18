package org.son.haruharu.admin.domain.cohort;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminCohortApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;

    private static final String TRACK_BODY = """
            {"key":"seoul-student","label":"서울 대학생","region":"서울","audience":"STUDENT","min_age":20,"max_age":29,
             "display_order":1,
             "questions":[{"key":"grade","label":"학년","type":"CHOICE","required":true,"options":["1학년","2학년"]},
                          {"key":"mbti","label":"MBTI","type":"TEXT"}]}""";

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(7L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String role, String body) throws Exception {
        request.with(authentication(auth(role)));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(request);
    }

    private long id(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private long createTrack(String key) throws Exception {
        return id(send(post("/api/v1/admin/recruit-tracks"), "OPERATOR", TRACK_BODY.replace("seoul-student", key))
                .andExpect(status().isOk()));
    }

    private String cohortBody(long trackId, int sequence, LocalDateTime opensAt, LocalDateTime closesAt) {
        return """
                {"track_id":%d,"sequence":%d,"apply_opens_at":"%s","apply_closes_at":"%s",
                 "assign_deadline_at":"%s","period_label":"10월 마지막 주","target_male":6,"target_female":6}"""
                .formatted(trackId, sequence, opensAt, closesAt, closesAt.plusDays(1));
    }

    private long createCohort(long trackId, int sequence) throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        return id(send(post("/api/v1/admin/cohorts"), "OPERATOR",
                cohortBody(trackId, sequence, now.minusDays(1), now.plusDays(5))).andExpect(status().isOk()));
    }

    @Test
    @DisplayName("운영진은 트랙을 만들고 문항과 함께 조회·수정할 수 있다")
    void operator_createsListsAndUpdatesTrack() throws Exception {
        long trackId = id(send(post("/api/v1/admin/recruit-tracks"), "OPERATOR", TRACK_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("seoul-student"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.questions[0].options[1]").value("2학년"))
                .andExpect(jsonPath("$.data.questions[1].required").value(false)));

        send(get("/api/v1/admin/recruit-tracks"), "CS", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].questions.length()").value(2));

        send(patch("/api/v1/admin/recruit-tracks/{id}", trackId), "OPERATOR", "{\"max_age\":30,\"questions\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.max_age").value(30))
                .andExpect(jsonPath("$.data.min_age").value(20))
                .andExpect(jsonPath("$.data.questions.length()").value(0));
    }

    @Test
    @DisplayName("트랙 키가 겹치면 409, 칸이 잘못되면 400 VALIDATION_ERROR 와 칸 이름을 준다")
    void trackDuplicateAndValidation() throws Exception {
        createTrack("seoul-student");
        send(post("/api/v1/admin/recruit-tracks"), "OPERATOR", TRACK_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RECRUIT_TRACK_KEY_DUPLICATE"));

        send(post("/api/v1/admin/recruit-tracks"), "OPERATOR",
                TRACK_BODY.replace("seoul-student", "busan").replace("\"min_age\":20", "\"min_age\":17"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("min_age"));
    }

    @Test
    @DisplayName("기수는 초안으로 만들어지고, 공개하면 신청 기간 안에서 is_accepting 이 true 가 된다")
    void cohortCreatePublishAndList() throws Exception {
        long trackId = createTrack("seoul-student");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        long cohortId = id(send(post("/api/v1/admin/cohorts"), "OPERATOR",
                cohortBody(trackId, 6, now.minusDays(1), now.plusDays(5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.name").value("서울 대학생 6기"))
                .andExpect(jsonPath("$.data.created_by").value("admin:7"))
                .andExpect(jsonPath("$.data.is_accepting").value(false)));

        send(post("/api/v1/admin/cohorts/{id}/publish", cohortId), "OPERATOR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.is_accepting").value(true));

        send(get("/api/v1/admin/cohorts").param("trackId", String.valueOf(trackId)).param("status", "OPEN"), "CS", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].track_label").value("서울 대학생"));
        send(get("/api/v1/admin/cohorts").param("status", "DRAFT"), "CS", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("트랙당 OPEN 기수는 하나뿐이고, 기존 기수를 취소하면 다음 기수를 공개할 수 있다")
    void onlyOneOpenCohortPerTrack() throws Exception {
        long trackId = createTrack("seoul-student");
        long sixth = createCohort(trackId, 6);
        long seventh = createCohort(trackId, 7);
        send(post("/api/v1/admin/cohorts/{id}/publish", sixth), "OPERATOR", null).andExpect(status().isOk());

        send(post("/api/v1/admin/cohorts/{id}/publish", seventh), "OPERATOR", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COHORT_OPEN_ALREADY_EXISTS"));

        send(post("/api/v1/admin/cohorts/{id}/cancel", sixth), "OPERATOR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        send(post("/api/v1/admin/cohorts/{id}/publish", seventh), "OPERATOR", null)
                .andExpect(status().isOk());
        // 취소된 기수는 다시 취소하거나 고칠 수 없다.
        send(post("/api/v1/admin/cohorts/{id}/cancel", sixth), "OPERATOR", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COHORT_NOT_CANCELLABLE"));
        send(patch("/api/v1/admin/cohorts/{id}", sixth), "OPERATOR", "{\"period_label\":\"11월\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COHORT_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("같은 트랙의 같은 번호는 409, 다른 트랙이면 같은 번호를 쓸 수 있다")
    void sequenceIsUniquePerTrack() throws Exception {
        long seoul = createTrack("seoul-student");
        long gwangju = createTrack("gwangju-student");
        createCohort(seoul, 6);

        LocalDateTime now = LocalDateTime.now().withNano(0);
        send(post("/api/v1/admin/cohorts"), "OPERATOR", cohortBody(seoul, 6, now, now.plusDays(3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COHORT_SEQUENCE_DUPLICATE"));
        createCohort(gwangju, 6);
    }

    @Test
    @DisplayName("공개 중인 기수가 있으면 트랙을 끌 수 없고, 꺼진 트랙의 기수는 공개할 수 없다")
    void inactiveTrackRules() throws Exception {
        long trackId = createTrack("seoul-student");
        long sixth = createCohort(trackId, 6);
        send(post("/api/v1/admin/cohorts/{id}/publish", sixth), "OPERATOR", null).andExpect(status().isOk());

        send(patch("/api/v1/admin/recruit-tracks/{id}", trackId), "OPERATOR", "{\"active\":false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RECRUIT_TRACK_HAS_OPEN_COHORT"));

        send(post("/api/v1/admin/cohorts/{id}/cancel", sixth), "OPERATOR", null).andExpect(status().isOk());
        send(patch("/api/v1/admin/recruit-tracks/{id}", trackId), "OPERATOR", "{\"active\":false}")
                .andExpect(status().isOk());
        long seventh = createCohort(trackId, 7);
        send(post("/api/v1/admin/cohorts/{id}/publish", seventh), "OPERATOR", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RECRUIT_TRACK_INACTIVE"));
    }

    @Test
    @DisplayName("신청 기간 순서가 틀리면 칸 이름과 함께 400, 마감이 지난 기수는 공개할 수 없다")
    void scheduleValidation() throws Exception {
        long trackId = createTrack("seoul-student");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        send(post("/api/v1/admin/cohorts"), "OPERATOR", cohortBody(trackId, 6, now.plusDays(3), now))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("apply_closes_at"));

        long past = id(send(post("/api/v1/admin/cohorts"), "OPERATOR",
                cohortBody(trackId, 5, now.minusDays(5), now.minusDays(1))).andExpect(status().isOk()));
        send(post("/api/v1/admin/cohorts/{id}/publish", past), "OPERATOR", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("apply_closes_at"));
    }

    @Test
    @DisplayName("문항·보기에 null 이 섞이면 400 VALIDATION_ERROR 와 칸 이름을 준다")
    void nullQuestionElements() throws Exception {
        send(post("/api/v1/admin/recruit-tracks"), "OPERATOR",
                TRACK_BODY.replace("\"questions\":[", "\"questions\":[null,"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("questions[0]"));
        long trackId = createTrack("seoul-student");
        send(patch("/api/v1/admin/recruit-tracks/{id}", trackId), "OPERATOR",
                "{\"questions\":[{\"key\":\"grade\",\"label\":\"학년\",\"type\":\"CHOICE\",\"options\":[\"A\",null]}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("questions[0].options"));
    }

    @Test
    @DisplayName("목록 필터 값의 형식이 틀리면 500 이 아니라 400 INVALID_TYPE")
    void invalidListFilters() throws Exception {
        send(get("/api/v1/admin/cohorts").param("status", "INVALID"), "CS", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TYPE"));
        send(get("/api/v1/admin/cohorts").param("trackId", "abc"), "CS", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TYPE"));
    }

    @Test
    @DisplayName("없는 기수·트랙은 404")
    void notFound() throws Exception {
        send(post("/api/v1/admin/cohorts/{id}/publish", 9999), "OPERATOR", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COHORT_NOT_FOUND"));
        LocalDateTime now = LocalDateTime.now().withNano(0);
        send(post("/api/v1/admin/cohorts"), "OPERATOR", cohortBody(9999, 6, now, now.plusDays(3)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECRUIT_TRACK_NOT_FOUND"));
    }

    @Test
    @DisplayName("CS 는 조회만 할 수 있고 쓰기는 403, 인증이 없으면 401")
    void permissions() throws Exception {
        send(post("/api/v1/admin/recruit-tracks"), "CS", TRACK_BODY).andExpect(status().isForbidden());
        long trackId = createTrack("seoul-student");
        long cohortId = createCohort(trackId, 6);
        send(patch("/api/v1/admin/recruit-tracks/{id}", trackId), "CS", "{\"label\":\"x\"}").andExpect(status().isForbidden());
        send(post("/api/v1/admin/cohorts"), "CS", cohortBody(trackId, 7, LocalDateTime.now(), LocalDateTime.now().plusDays(1)))
                .andExpect(status().isForbidden());
        send(patch("/api/v1/admin/cohorts/{id}", cohortId), "CS", "{\"period_label\":\"x\"}").andExpect(status().isForbidden());
        send(post("/api/v1/admin/cohorts/{id}/publish", cohortId), "CS", null).andExpect(status().isForbidden());
        send(post("/api/v1/admin/cohorts/{id}/cancel", cohortId), "CS", null).andExpect(status().isForbidden());
        send(get("/api/v1/admin/cohorts"), "CS", null).andExpect(status().isOk());
        send(post("/api/v1/admin/cohorts/{id}/publish", cohortId), "SUPER", null).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/recruit-tracks")).andExpect(status().isUnauthorized());
    }
}
