package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 매일 새벽 4시 30분, 탈퇴한 지 30일이 지난 본인인증(CI/DI)을 물리 파기한다(개인정보 보관정책).
 * 재인증 시도 여부와 무관하게 보관 기간 경과분을 자동 정리해, 소프트삭제로 남은 CI/DI가 무기한 잔존하지 않도록 보장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityCleanupScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int RETENTION_DAYS = 30;

    private final UserIdentityWriter userIdentityWriter;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void purgeExpiredWithdrawn() {
        try {
            LocalDateTime threshold = LocalDateTime.now(SEOUL).minusDays(RETENTION_DAYS);
            int purged = userIdentityWriter.purgeWithdrawnBefore(threshold);
            log.info("탈퇴 본인인증 정리 완료: {}건 파기", purged);
        } catch (Exception e) {
            log.error("탈퇴 본인인증 정리 실패", e);
        }
    }
}
