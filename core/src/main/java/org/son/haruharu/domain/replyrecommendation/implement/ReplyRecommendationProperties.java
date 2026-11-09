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
         * 켜 둘 방(시험 공개). 비어 있으면 모든 방이다. 운영은 개인정보처리방침 개정 전까지 운영진 시험 방만 넣고,
         * 개정·공지 뒤 비워 전체에 연다(2026-11-09 결정).
         */
        @DefaultValue List<Long> roomIds
) {
    /** 이 방에서 기능이 켜져 있는지. 분석 요청·추천·사건·답장 연결이 모두 이 판단을 따른다. */
    public boolean enabledFor(Long roomId) {
        return enabled && (roomIds.isEmpty() || roomIds.contains(roomId));
    }

    /** 실사용자 자료(사건·답장 연결·답장 원문) 수집. 꺼져 있으면 QA 계정만 남긴다. */
    public record Collection(@DefaultValue("false") boolean realUserEnabled, @DefaultValue List<Long> qaUserIds) {}

    public record Snapshot(@DefaultValue("3000") long sweepDelayMs,
                           @DefaultValue("20") int batchSize,
                           @DefaultValue("5") int maxAttempts,
                           @DefaultValue("30d") Duration retention) {}
}
