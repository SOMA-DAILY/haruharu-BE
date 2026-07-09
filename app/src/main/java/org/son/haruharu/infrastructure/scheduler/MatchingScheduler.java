package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.matching.business.MatchingExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 매칭 트리거. 실제 로직은 executor 유스케이스에 있고, 여기서는 시각에 맞춰 호출만 한다.
 * - 22:00: 대기 큐에서 남3·여3을 묶어 방 확정(scheduledDate = 익일). 확정 즉시 RoomMatchedEvent로 결과 푸시 발송
 * (09:00 세션 종료·방 활성화는 실행 순서 보장을 위해 MorningRolloverScheduler로 이동 — #170)
 * cron의 zone과 본문의 now() 기준 타임존을 모두 Asia/Seoul로 고정해 날짜 계산이 어긋나지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MatchingExecutor matchingExecutor;

    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
    public void runMatching() {
        try {
            int rooms = matchingExecutor.execute(LocalDate.now(SEOUL).plusDays(1), LocalDateTime.now(SEOUL));
            log.info("매칭 배치 완료: {}개 방 확정", rooms);
        } catch (Exception e) {
            log.error("매칭 배치 실패", e);
        }
    }
}
