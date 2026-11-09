package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.son.haruharu.domain.replyrecommendation.implement.exception.RecommendationRateLimitedException;
import org.springframework.stereotype.Component;

/**
 * 새 AI 작업을 만드는 요청의 시청자당 분당 한도(서버 한 대 기준 메모리). 준비된 결과 조회는 세지 않는다.
 * 서버가 여러 대면 대수만큼 느슨해진다. 비용은 작업기의 하루 한도가 최종적으로 막는다.
 * 시청자별 창은 ConcurrentHashMap.compute 안에서만 바꿔 정리와 기록이 서로 끼어들지 않는다.
 */
@Component
public class EnsureRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limit;
    private final Map<Long, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public EnsureRateLimiter(ReplyRecommendationProperties properties) {
        this.limit = properties.ensureRateLimitPerMinute();
    }

    public void acquire(Long viewerId, Instant now) {
        if (limit <= 0) {
            return;
        }
        Instant since = now.minus(WINDOW);
        boolean[] allowed = {false};
        windows.compute(viewerId, (ignored, window) -> {
            Deque<Instant> current = window == null ? new ArrayDeque<>() : window;
            while (!current.isEmpty() && !current.peekFirst().isAfter(since)) {
                current.pollFirst();
            }
            if (current.size() < limit) {
                current.addLast(now);
                allowed[0] = true;
            }
            return current;
        });
        if (!allowed[0]) {
            throw new RecommendationRateLimitedException();
        }
        if (windows.size() > CLEANUP_THRESHOLD) {
            for (Long key : windows.keySet()) {
                windows.computeIfPresent(key, (ignored, window) ->
                        window.isEmpty() || !window.peekLast().isAfter(since) ? null : window);
            }
        }
    }
}
