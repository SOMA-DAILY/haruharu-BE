package org.son.haruharu.domain.video;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ReactionType을 enum DTO로 전환한 뒤, 허용하지 않는 값은 역직렬화 단계에서 400으로 거부되는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReactionRequestValidationIntegrationTest {

    @Autowired MockMvc mockMvc;

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("허용하지 않는 reactionType으로 반응 요청 시 400을 반환한다")
    void givenInvalidReactionType_whenReact_thenBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/video-posts/1/reactions")
                        .with(authentication(auth(1L)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"reactionType\": \"ANGRY\"}"))
                .andExpect(status().isBadRequest());
    }
}
