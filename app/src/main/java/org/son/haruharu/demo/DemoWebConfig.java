package org.son.haruharu.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.context.annotation.Profile;

/** 데모 인가 인터셉터를 /api/v1/demo/** 에 등록(#264). */
@Configuration
@Profile("!prod") // 데모는 dev/local/test에서만 로드, prod 제외(#284)
public class DemoWebConfig implements WebMvcConfigurer {

    private final DemoModeInterceptor demoModeInterceptor;

    public DemoWebConfig(DemoModeInterceptor demoModeInterceptor) {
        this.demoModeInterceptor = demoModeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(demoModeInterceptor).addPathPatterns("/api/v1/demo/**");
    }
}
