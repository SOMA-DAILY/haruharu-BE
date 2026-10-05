package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.mission.business.OperatorMissionDeliveryService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OperatorMissionScheduler {
    private final OperatorMissionDeliveryService service;

    @Scheduled(fixedDelayString = "${haruharu.mission.operator-poll-ms:1000}")
    public void deliver() {
        for (Long id : service.dueIds(50)) {
            try {
                service.deliver(id);
            } catch (Exception e) {
                log.error("운영 미션 발송 실패: missionId={}", id, e);
                service.fail(id);
            }
        }
        for (Long id : service.pendingDeleteIds(50)) {
            try {
                service.delete(id);
            } catch (Exception e) {
                log.error("운영 미션 삭제 전달 실패: missionId={}", id, e);
            }
        }
    }
}
