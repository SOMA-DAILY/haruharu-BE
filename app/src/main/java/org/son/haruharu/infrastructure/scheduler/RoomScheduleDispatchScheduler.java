package org.son.haruharu.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.config.ChatFeatureProperties;
import org.son.haruharu.domain.roomschedule.business.RoomScheduleDispatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 방 편성 발송 잡(#457). 플래그 haruharu.chat.admin-message.enabled 가 true 일 때만 뜬다.
 * 행 단위 claim(조건부 UPDATE)이 멱등을 잡아 Spring Batch JobRepository 없이 app/batch 밖에 둔다.
 * 시스템 유저 id(CHAT_SYSTEM_USER_ID)가 없으면 기동을 막는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "haruharu.chat.admin-message", name = "enabled", havingValue = "true")
public class RoomScheduleDispatchScheduler {

    private final RoomScheduleDispatchService dispatchService;

    public RoomScheduleDispatchScheduler(RoomScheduleDispatchService dispatchService,
                                         ChatFeatureProperties chatFeatureProperties) {
        if (chatFeatureProperties.systemUserId() == null) {
            throw new IllegalStateException("haruharu.chat.admin-message.enabled=true 인데 haruharu.chat.system-user-id"
                    + "(CHAT_SYSTEM_USER_ID) 가 없습니다. scripts/sql/455-chat-p0-base-verify.sql 로 시스템 유저 id 를 확인해 설정하세요.");
        }
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
