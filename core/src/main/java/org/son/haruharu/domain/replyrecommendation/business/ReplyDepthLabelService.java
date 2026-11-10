package org.son.haruharu.domain.replyrecommendation.business;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthCollector;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthProperties;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthTarget;
import org.springframework.stereotype.Service;

/**
 * 스케줄러가 부르는 Depth 라벨 수집(#559). 스위치가 꺼져 있거나 559 표가 없으면 아무것도 하지 않는다.
 * 답장 하나가 실패해도 나머지를 계속한다. 같은 답장이 {@code max-attempts} 번 연속 실패하면 미관측(LABEL_FAILED)으로
 * 닫아 보낸 순서 앞자리를 계속 차지하지 않게 한다(실패 횟수는 서버 메모리에만 센다). 로그에는 id·개수만 남긴다.
 */
@Service
@RequiredArgsConstructor
public class ReplyDepthLabelService {

    private static final Logger log = LoggerFactory.getLogger(ReplyDepthLabelService.class);

    private final ReplyDepthProperties properties;
    private final ReplyDepthCollector collector;
    private final Clock clock;
    private final Map<Long, Integer> failures = new ConcurrentHashMap<>();

    /** 관찰 기간이 끝난 답장의 라벨을 만든다. 새로 넣은 개수를 돌려준다. */
    public int collectDue() {
        if (!properties.enabled() || !collector.ready()) {
            return 0;
        }
        Instant now = clock.instant();
        List<ReplyDepthTarget> due = collector.findDue(now);
        int observed = 0;
        for (ReplyDepthTarget target : due) {
            try {
                if (collector.observe(target, now)) {
                    observed++;
                }
                failures.remove(target.messageId());
            } catch (RuntimeException failure) {
                int attempts = failures.merge(target.messageId(), 1, Integer::sum);
                log.atWarn().setCause(failure).addKeyValue("event", "REPLY_DEPTH_LABEL_FAILED")
                        .addKeyValue("messageId", target.messageId()).addKeyValue("attempts", attempts)
                        .log("답장 Depth 라벨 실패");
                if (attempts >= properties.maxAttempts()) {
                    closeAsFailed(target, now);
                }
            }
        }
        if (observed > 0) {
            log.atInfo().addKeyValue("event", "REPLY_DEPTH_LABELED").addKeyValue("count", observed)
                    .log("답장 Depth 라벨 수집");
        }
        return observed;
    }

    private void closeAsFailed(ReplyDepthTarget target, Instant now) {
        try {
            collector.closeAsFailed(target, now);
            failures.remove(target.messageId());
        } catch (RuntimeException failure) {
            log.atWarn().setCause(failure).addKeyValue("event", "REPLY_DEPTH_LABEL_CLOSE_FAILED")
                    .addKeyValue("messageId", target.messageId()).log("답장 Depth 라벨 미관측 닫기 실패");
        }
    }
}
