package org.son.haruharu.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.config.TimeZoneConfig;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * admin 모듈은 core의 config 패키지를 컴포넌트 스캔하지 않으므로(스캔 범위 = org.son.haruharu.admin),
 * JVM 기본 타임존을 Asia/Seoul로 고정하는 TimeZoneConfig를 @Import로 명시해야 한다.
 * 누락 시 admin의 JVM 기본 타임존이 컨테이너 기본값(UTC)으로 동작한다. (#220)
 */
class AdminApplicationTimeZoneTest {

    @Test
    @DisplayName("AdminApplication의 @Import에 TimeZoneConfig가 포함된다 (JVM 기본 타임존 Asia/Seoul 고정)")
    void givenAdminApplication_whenReadImport_thenContainsTimeZoneConfig() {
        Import importAnnotation = AdminApplication.class.getAnnotation(Import.class);

        assertThat(importAnnotation).isNotNull();
        assertThat(importAnnotation.value()).contains(TimeZoneConfig.class);
    }
}
