package org.son.haruharu.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * access log에 traceId·userId가 실제로 붙는지 필터 체인 전체를 태워 확인한다(#259 2-3).
 * MdcLoggingFilter가 Security 필터체인보다 앞(HIGHEST_PRECEDENCE)에 있어야 성립하는 계약이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessLogIntegrationTest {

    private static final String VALID_ROOT = "1-67891233-abcdef012345678912345678";

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JwtProvider jwtProvider;

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        userJpaRepository.deleteAll();
        logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("인증된 요청의 access log에 traceId와 userId가 함께 붙는다")
    void givenAuthenticatedRequest_whenAccessLogged_thenHasTraceIdAndUserId() throws Exception {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        String token = jwtProvider.generateAccessToken(userId);

        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .header("X-Amzn-Trace-Id", "Root=" + VALID_ROOT));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getMDCPropertyMap()).containsEntry("traceId", VALID_ROOT);
        assertThat(event.getMDCPropertyMap()).containsEntry("userId", String.valueOf(userId));
        assertThat(event.getFormattedMessage()).contains("GET").contains("/api/v1/users/me");
    }

    @Test
    @DisplayName("인증되지 않은 요청의 access log에도 traceId는 붙고 userId는 '-'로 남는다")
    void givenUnauthenticatedRequest_whenAccessLogged_thenUserIdIsDash() throws Exception {
        mockMvc.perform(get("/api/v1/matching/__probe")
                .header("X-Amzn-Trace-Id", "Root=" + VALID_ROOT));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getMDCPropertyMap()).containsEntry("traceId", VALID_ROOT);
        assertThat(event.getMDCPropertyMap()).containsEntry("userId", "-");
    }

    @Test
    @DisplayName("헬스체크는 access log에 남지 않는다")
    void givenActuatorHealth_whenRequested_thenNoAccessLog() throws Exception {
        mockMvc.perform(get("/actuator/health"));

        assertThat(appender.list).isEmpty();
    }
}
