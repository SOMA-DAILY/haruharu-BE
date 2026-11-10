package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.replyrecommendation.business.ReplyDepthLabelService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 영상 답장 Depth 라벨 수집(#559). 스위치(haruharu.reply-recommendation.depth.enabled)가 꺼져 있으면 아무것도 하지 않는다.
 * 이미 라벨이 있는 답장은 건너뛰므로 서버가 여러 대여도 결과는 하나다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplyDepthScheduler {

    private final ReplyDepthLabelService service;

    /** 매 10분 40초(다른 답장 추천 잡과 다른 초). */
    @Scheduled(cron = "${haruharu.reply-recommendation.depth.cron:40 */10 * * * *}", zone = "Asia/Seoul")
    public void collect() {
        try {
            service.collectDue();
        } catch (Exception e) {
            log.error("답장 Depth 라벨 잡 실패", e);
        }
    }
}
