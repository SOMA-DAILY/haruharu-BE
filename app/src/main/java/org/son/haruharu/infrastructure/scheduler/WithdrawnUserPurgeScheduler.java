package org.son.haruharu.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.user.business.WithdrawnUserPurgeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 매일 새벽 4시 45분, 탈퇴한 지 90일이 지난 회원의 개인정보를 파기한다(#555). CI/DI 30일 스케줄러(04:30) 다음.
 * id 커서로 100명씩 끝까지 돌고, 회원마다 트랜잭션을 따로 열어 실패는 로그만 남긴 뒤 다음 회원으로 간다.
 * 실패분은 purged_at 이 비어 있어 다음 날 재시도. 로그에는 userId 와 건수만 남긴다.
 */
@Slf4j
@Component
public class WithdrawnUserPurgeScheduler {

    private final WithdrawnUserPurgeService withdrawnUserPurgeService;

    public WithdrawnUserPurgeScheduler(WithdrawnUserPurgeService withdrawnUserPurgeService) {
        this.withdrawnUserPurgeService = withdrawnUserPurgeService;
    }

    @Scheduled(cron = "${haruharu.user.withdrawn-purge.cron:0 45 4 * * *}", zone = "Asia/Seoul")
    public void purgeExpiredWithdrawnUsers() {
        int total = 0;
        int succeeded = 0;
        long cursor = 0L;
        while (true) {
            List<Long> userIds;
            try {
                userIds = withdrawnUserPurgeService.findDueUserIds(cursor);
            } catch (RuntimeException e) {
                log.error("탈퇴 회원 파기 대상 조회 실패 cursor={}", cursor, e);
                break;
            }
            if (userIds.isEmpty()) {
                break;
            }
            for (Long userId : userIds) {
                total++;
                try {
                    Map<String, Integer> counts = withdrawnUserPurgeService.purge(userId);
                    succeeded++;
                    log.info("탈퇴 회원 개인정보 파기 userId={} {}", userId, counts);
                } catch (RuntimeException e) {
                    log.error("탈퇴 회원 개인정보 파기 실패 userId={}", userId, e);
                }
            }
            cursor = userIds.get(userIds.size() - 1);
        }
        if (total > 0) {
            log.info("탈퇴 회원 파기 완료: 대상 {}명, 성공 {}명", total, succeeded);
        }
    }
}
