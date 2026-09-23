package org.son.haruharu.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 폐기된 자동매칭 API가 과거 설정으로 재활성화되지 않는지 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "haruharu.matching.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class AutoMatchingRemovedIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserJpaRepository userJpaRepository;
    @Autowired
    JwtProvider jwtProvider;

    @BeforeEach
    void clean() {
        userJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("폐기된 자동매칭은 과거 활성화 설정이 남아 있어도 API가 등록되지 않는다")
    void givenObsoleteFlagEnabled_whenActiveUserCallsQueue_thenNotFound() throws Exception {
        // given
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        String token = jwtProvider.generateAccessToken(userId);

        // when & then
        mockMvc.perform(get("/api/v1/matching/queue")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "DELETE"})
    @DisplayName("실제 HTTP 서버에서도 폐기된 큐 조회·참가·취소 API는 404다")
    void givenObsoleteFlagEnabled_whenHttpCallsQueue_thenNotFound(String method) throws Exception {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null)))
                .toDomain().id();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/matching/queue"))
                .header("Authorization", "Bearer " + jwtProvider.generateAccessToken(userId))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        }
    }

    @Test
    @DisplayName("실제 HTTP 메타 응답은 세션 정책을 유지하고 자동매칭 정책을 제거한다")
    void givenNoAuth_whenHttpGetsMeta_thenOnlySessionPolicyRemains() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/meta"))
                .GET().build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"session\"").doesNotContain("\"matching\"", "enter_cost");
        }
    }

    @Test
    @DisplayName("개발 API가 활성인 로컬 서버에서도 자동매칭 수동 실행 API는 없다")
    void givenLocalProfile_whenHttpCallsDevApi_thenOnlyMatchingRunIsRemoved() throws Exception {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null)))
                .toDomain().id();
        String authorization = "Bearer " + jwtProvider.generateAccessToken(userId);
        HttpRequest clock = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/dev/clock"))
                .header("Authorization", authorization).GET().build();
        HttpRequest matching = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/dev/matching/run"))
                .header("Authorization", authorization).POST(HttpRequest.BodyPublishers.noBody()).build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(client.send(clock, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
            assertThat(client.send(matching, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
        }
    }
}
