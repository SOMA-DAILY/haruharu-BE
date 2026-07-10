package org.son.haruharu.config;

import org.son.haruharu.common.time.AdjustableClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 주입 가능한 Clock 빈. 시간 구동 로직(룸 currentDay 파생, 슬롯 시간창 검증, Day3 게이트)이
 * 테스트에서 시각을 고정/이동할 수 있도록 now()를 직접 호출하지 않고 이 Clock을 주입받는다.
 * 타임존은 Asia/Seoul 고정([[TimeZoneConfig]]와 동일 기준).
 *
 * local·dev 프로필에서는 가상 시각을 고정할 수 있는 AdjustableClock(#180)으로 대체된다.
 * core에 두는 이유: admin 모듈도 dev 프로필로 기동하므로 Clock 빈이 양쪽 모듈에서 항상 존재해야 한다.
 */
@Configuration
public class ClockConfig {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Bean
    @Profile("!local & !dev")
    public Clock clock() {
        return Clock.system(SEOUL);
    }

    @Bean
    @Profile({"local", "dev"})
    public AdjustableClock adjustableClock() {
        return new AdjustableClock(Clock.system(SEOUL));
    }
}
