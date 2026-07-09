package org.son.haruharu.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * 런타임 직렬화는 Jackson 3(tools.jackson)로 전역 SNAKE_CASE가 적용되지만,
     * springdoc의 스키마 생성은 swagger-core가 쓰는 별도 Jackson 2(com.fasterxml) 매퍼를 사용해
     * 전역 전략이 반영되지 않는다(필드명이 camelCase로 노출). 스키마 매퍼에도 동일한 SNAKE_CASE를
     * 지정해 실제 요청/응답 필드명(snake_case)과 문서를 일치시킨다. (@JsonProperty 개별 오버라이드는 유지됨)
     */
    @Bean
    public ModelResolver modelResolver() {
        ObjectMapper schemaMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(schemaMapper);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info().title("Haruharu API").version("v1"))
            .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
            .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}