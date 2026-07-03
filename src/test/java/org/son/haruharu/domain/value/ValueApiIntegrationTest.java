package org.son.haruharu.domain.value;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.value.implement.UserValueAnswer;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.storage.UserValueAnswerEntity;
import org.son.haruharu.domain.value.storage.UserValueAnswerJpaRepository;
import org.son.haruharu.domain.value.storage.ValueQuestionEntity;
import org.son.haruharu.domain.value.storage.ValueQuestionJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ValueApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ValueQuestionJpaRepository valueQuestionJpaRepository;
    @Autowired UserValueAnswerJpaRepository userValueAnswerJpaRepository;

    @BeforeEach
    void clean() {
        userValueAnswerJpaRepository.deleteAll();
        valueQuestionJpaRepository.deleteAll();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Long saveQuestion(String text, String category, int displayOrder, boolean active) {
        ValueQuestionEntity saved = valueQuestionJpaRepository.save(
                ValueQuestionEntity.from(new ValueQuestion(null, text, category, displayOrder, active))
        );
        return saved.toDomain().id();
    }

    @Test
    @DisplayName("활성 질문만 display_order 오름차순으로 snake_case 응답된다")
    void givenActiveAndInactiveQuestions_whenGetQuestions_thenReturnsOnlyActiveSorted() throws Exception {
        Long second = saveQuestion("두 번째 질문", "RELATIONSHIP", 2, true);
        Long first = saveQuestion("첫 번째 질문", "LIFESTYLE", 1, true);
        saveQuestion("비활성 질문", "LIFESTYLE", 3, false);

        mockMvc.perform(get("/api/v1/value-questions")
                        .with(authentication(auth(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andExpect(jsonPath("$.data.questions[0].question_id").value(first.intValue()))
                .andExpect(jsonPath("$.data.questions[0].question_text").value("첫 번째 질문"))
                .andExpect(jsonPath("$.data.questions[0].display_order").value(1))
                .andExpect(jsonPath("$.data.questions[1].question_id").value(second.intValue()))
                .andExpect(jsonPath("$.data.questions[1].category").value("RELATIONSHIP"));
    }

    @Test
    @DisplayName("인증 없이 활성 질문을 조회해도 200으로 응답한다(permitAll)")
    void givenNoAuth_whenGetQuestions_thenReturnsOk() throws Exception {
        saveQuestion("질문", "LIFESTYLE", 1, true);

        mockMvc.perform(get("/api/v1/value-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.questions.length()").value(1));
    }

    @Test
    @DisplayName("답변 일괄 저장 시 saved_count를 반환하고 user_value_answers에 저장된다")
    void givenAnswers_whenPostAnswers_thenSavesAndReturnsCount() throws Exception {
        Long userId = 1L;
        Long q1 = saveQuestion("질문1", "LIFESTYLE", 1, true);
        Long q2 = saveQuestion("질문2", "LIFESTYLE", 2, true);
        String body = """
                { "answers": [
                  { "question_id": %s, "answer": "등산" },
                  { "question_id": %s, "answer": "독서" }
                ] }
                """.formatted(q1, q2);

        mockMvc.perform(post("/api/v1/value-answers")
                        .with(authentication(auth(userId)))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.saved_count").value(2));

        List<UserValueAnswer> saved = userValueAnswerJpaRepository.findAllByUserId(userId)
                .stream().map(UserValueAnswerEntity::toDomain).toList();
        assertThat(saved)
                .extracting(UserValueAnswer::questionId)
                .containsExactlyInAnyOrder(q1, q2);
    }

    @Test
    @DisplayName("같은 questionId를 재제출하면 답변이 갱신되고 행은 하나만 유지된다(멱등)")
    void givenResubmittedAnswer_whenPostAnswers_thenUpsertsSingleRow() throws Exception {
        Long userId = 1L;
        Long questionId = saveQuestion("질문", "LIFESTYLE", 1, true);

        mockMvc.perform(post("/api/v1/value-answers")
                        .with(authentication(auth(userId)))
                        .contentType(APPLICATION_JSON)
                        .content("{ \"answers\": [ { \"question_id\": %s, \"answer\": \"등산\" } ] }".formatted(questionId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/value-answers")
                        .with(authentication(auth(userId)))
                        .contentType(APPLICATION_JSON)
                        .content("{ \"answers\": [ { \"question_id\": %s, \"answer\": \"독서\" } ] }".formatted(questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saved_count").value(1));

        List<UserValueAnswer> saved = userValueAnswerJpaRepository.findAllByUserId(userId)
                .stream().map(UserValueAnswerEntity::toDomain).toList();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).answer()).isEqualTo("독서");
    }

    @Test
    @DisplayName("빈 answers로 요청 시 400을 반환한다")
    void givenEmptyAnswers_whenPostAnswers_thenReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/value-answers")
                        .with(authentication(auth(1L)))
                        .contentType(APPLICATION_JSON)
                        .content("{ \"answers\": [] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }
}
