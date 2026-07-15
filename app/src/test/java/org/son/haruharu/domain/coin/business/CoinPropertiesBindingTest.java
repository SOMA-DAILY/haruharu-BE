package org.son.haruharu.domain.coin.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정책값이 코드가 아니라 application.yaml 리터럴로 관리되는지 검증한다(#232).
 * 실제 application.yaml을 로드(ConfigData)해 haruharu.coin.signup-bonus 바인딩값을 확인한다.
 */
class CoinPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class);

    @EnableConfigurationProperties(CoinProperties.class)
    static class Config {
    }

    @Test
    @DisplayName("application.yaml의 haruharu.coin.signup-bonus가 첫 가입 보상 20으로 바인딩된다")
    void givenApplicationYaml_whenBind_thenSignupBonusIs20() {
        runner.run(ctx -> assertThat(ctx.getBean(CoinProperties.class).signupBonus()).isEqualTo(20L));
    }
}
