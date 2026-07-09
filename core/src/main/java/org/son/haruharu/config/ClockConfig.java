package org.son.haruharu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 주입 가능한 Clock 빈. 시간 구동 로직(룸 currentDay 파생, 슬롯 시간창 검증, Day3 게이트)이
 * 테스트에서 시각을 고정/이동할 수 있도록 now()를 직접 호출하지 않고 이 Clock을 주입받는다.
 * 타임존은 Asia/Seoul 고정([[TimeZoneConfig]]와 동일 기준).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
