package org.son.haruharu.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.mission.chat.business.MissionDispatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 방 편성 발송 잡(#457). 플래그 haruharu.chat.admin-message.enabled 가 true 일 때만 뜬다.
 * 행 단위 claim(조건부 UPDATE)이 멱등을 잡아 Spring Batch JobRepository 없이 app/batch 밖에 둔다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "haruharu.chat.admin-message", name = "enabled", havingValue = "true")
public class MissionDispatchScheduler {

    private final MissionDispatchService dispatchService;

    public MissionDispatchScheduler(MissionDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(cron = "${haruharu.chat.admin-message.cron:20 * * * * *}", zone = "Asia/Seoul")
    public void dispatch() {
        try {
            int sent = dispatchService.dispatchDue();
            if (sent > 0) {
                log.info("방 편성 발송: {}건", sent);
            }
        } catch (Exception e) {
            log.error("방 편성 발송 잡 실패", e);
        }
    }
}
