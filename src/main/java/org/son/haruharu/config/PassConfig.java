package org.son.haruharu.config;

import org.son.haruharu.infrastructure.pass.PortOneProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(PortOneProperties.class)
public class PassConfig {

    // 외부 본인인증 제공자(PortOne) 호출이 무한 대기하지 않도록 타임아웃을 명시한다.
    // 초과 시 ResourceAccessException 이 발생하고 PassVerificationAdapter 가 원인을 로깅한다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    // Spring Boot 4.1은 RestClient.Builder 빈을 자동 등록하지 않아 명시적으로 제공한다.
    @Bean
    public RestClient.Builder restClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
