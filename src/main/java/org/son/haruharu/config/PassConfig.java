package org.son.haruharu.config;

import org.son.haruharu.infrastructure.pass.PortOneProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PortOneProperties.class)
public class PassConfig {

    // Spring Boot 4.1은 RestClient.Builder 빈을 자동 등록하지 않아 명시적으로 제공한다.
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
