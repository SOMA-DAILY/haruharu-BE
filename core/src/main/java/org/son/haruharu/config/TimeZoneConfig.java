package org.son.haruharu.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * JVM 기본 타임존을 Asia/Seoul로 고정한다.
 * 타임존을 명시하지 않은 LocalDateTime.now() 등이 서버 환경(UTC)과 무관하게 서울 시각으로 동작하게 해
 * 스케줄러(now(SEOUL))와 요청 스레드의 시각 기록을 일관시킨다.
 */
@Configuration
public class TimeZoneConfig {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }
}
