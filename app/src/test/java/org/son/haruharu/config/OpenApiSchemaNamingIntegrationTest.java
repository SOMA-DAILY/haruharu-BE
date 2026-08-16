package org.son.haruharu.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * springdoc 스키마 매퍼(ModelResolver)가 전역 SNAKE_CASE를 따르는지 검증한다(#161).
 * 런타임 직렬화(Jackson 3)와 문서 스키마(swagger-core의 Jackson 2)가 별도 매퍼라,
 * 이 설정이 없으면 요청 필드가 camelCase로 잘못 노출된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSchemaNamingIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("api-docs 요청 스키마 필드는 실제 API와 동일한 snake_case로 노출된다")
    void givenSnakeCaseStrategy_whenApiDocs_thenRequestFieldsAreSnakeCase() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.VideoUploadRequest.properties.video_url").exists())
                .andExpect(jsonPath("$.components.schemas.VideoUploadRequest.properties.videoUrl").doesNotExist());
    }

    @Test
    @DisplayName("공통 응답 래퍼의 isSuccess는 @JsonProperty 오버라이드로 camelCase를 유지한다")
    void givenJsonPropertyOverride_whenApiDocs_thenIsSuccessStaysCamelCase() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ApiResponseHomeResponse.properties.isSuccess").exists());
    }
}
