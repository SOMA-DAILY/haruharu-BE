package org.son.haruharu.admin.config;

import org.son.haruharu.admin.security.AdminAccessLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 어드민 API 전체에 접속기록 인터셉터를 건다(#554). 제외 규칙은 {@code AdminAuditActionResolver} 가 가진다. */
@Configuration
public class AdminAuditConfig implements WebMvcConfigurer {

    private final AdminAccessLogInterceptor adminAccessLogInterceptor;

    public AdminAuditConfig(AdminAccessLogInterceptor adminAccessLogInterceptor) {
        this.adminAccessLogInterceptor = adminAccessLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAccessLogInterceptor).addPathPatterns("/api/v1/admin/**");
    }
}
