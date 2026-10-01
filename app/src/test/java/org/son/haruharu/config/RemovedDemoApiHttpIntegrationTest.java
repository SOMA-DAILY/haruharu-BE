package org.son.haruharu.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RemovedDemoApiHttpIntegrationTest {

    @LocalServerPort int port;
    @Autowired UserJpaRepository userRepository;
    @Autowired JwtProvider jwtProvider;

    @ParameterizedTest(name = "삭제된 데모 API {0}는 인증 예외와 핸들러가 없다")
    @ValueSource(strings = {"login", "reset", "match-result"})
    void givenRemovedEndpoint_whenRequested_thenAuthenticationIsEnforcedAndHandlerIsAbsent(String action)
            throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(request(client, action, null).statusCode()).isEqualTo(401);

            Long bannedId = userRepository.save(UserEntity.from(new User(null, UserStatus.BANNED, null))).toDomain().id();
            HttpResponse<String> banned = request(client, action, jwtProvider.generateAccessToken(bannedId));
            assertThat(banned.statusCode()).isEqualTo(403);
            assertThat(banned.body()).contains("USER_INACTIVE");

            Long activeId = userRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
            assertThat(request(client, action, jwtProvider.generateAccessToken(activeId)).statusCode()).isEqualTo(404);
        }
    }

    private HttpResponse<String> request(HttpClient client, String action, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/demo/" + action))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"unused\"}"));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
