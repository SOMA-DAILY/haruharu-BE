package org.son.haruharu.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class TimeZoneConfigTest {

    @Test
    @DisplayName("초기화 시 JVM 기본 타임존이 Asia/Seoul로 고정된다")
    void givenUtcDefault_whenInit_thenDefaultIsSeoul() {
        // given — 호스트 타임존과 무관하게 RED를 보장하기 위해 UTC로 강제
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // when
        new TimeZoneConfig().init();

        // then
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
