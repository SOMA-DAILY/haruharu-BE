package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 영상 답장 추천(#558) 앱 서버 설정. 기능·개인화·실사용자 수집은 모두 기본 OFF 다.
 * 작업기(모델·비용·배치) 설정은 ai-worker 가 따로 가진다.
 */
@ConfigurationProperties(prefix = "haruharu.reply-recommendation")
public record ReplyRecommendationProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("false") boolean personalizationEnabled,
        /** 새 AI 작업을 만드는 추천 요청의 시청자당 분당 한도. 준비된 결과를 돌려주는 요청은 세지 않는다. */
        @DefaultValue("10") int ensureRateLimitPerMinute,
        @DefaultValue("1000") long pendingRetryAfterMs,
        /** 비용 한도·전송 실패처럼 다시 해 볼 만한 실패의 UNAVAILABLE 유지 시간. */
        @DefaultValue("30m") Duration transientUnavailableTtl,
        /** 거절·형식 위반 같은 실패의 UNAVAILABLE 유지 시간. */
        @DefaultValue("24h") Duration unavailableTtl,
        @DefaultValue Collection collection,
        @DefaultValue Snapshot snapshot,
        /**
         * 방 칸(rooms.reply_recommendation_enabled)과 별도로 켜 둘 방. 기존 시험 방 호환용이며 비어 있으면 추가 방이 없다.
         * 방별 판단은 {@link ReplyRecommendationRoomGate} 가 한다(#560).
         */
        @DefaultValue List<Long> roomIds
) {

    /** 실사용자 자료(사건·답장 연결·답장 원문) 수집. 꺼져 있으면 QA 계정만 남긴다. */
    public record Collection(@DefaultValue("false") boolean realUserEnabled, @DefaultValue List<Long> qaUserIds) {}

    public record Snapshot(@DefaultValue("3000") long sweepDelayMs,
                           @DefaultValue("20") int batchSize,
                           @DefaultValue("5") int maxAttempts,
                           @DefaultValue("30d") Duration retention) {}
}
