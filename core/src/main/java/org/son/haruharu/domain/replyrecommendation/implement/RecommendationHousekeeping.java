package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Component;

/**
 * 뒷정리: 24시간 답장 없던 노출 닫기, 삭제·숨김 영상의 추천 막기와 파생 자료 삭제 작업, 보관 기간(기본 30일)이 지난
 * 원문·파생 자료 지우기. 지운 뒤에는 해시·id·삭제 사실만 남는다(명세 5장).
 */
@Component
@RequiredArgsConstructor
public class RecommendationHousekeeping {

    static final Duration EPISODE_WINDOW = Duration.ofHours(24);
    static final String VIDEO_UNAVAILABLE = "VIDEO_UNAVAILABLE";

    private final AnalysisPipelineRepository pipeline;
    private final RecommendationFeedbackRepository feedback;
    private final VideoPostReader videoPosts;
    private final org.son.haruharu.domain.chat.implement.ChatMessageReader chatMessages;
    private final ReplyRecommendationProperties properties;

    public boolean ready() {
        return pipeline.schemaReady();
    }

    public int closeExpiredEpisodes(Instant now) {
        return feedback.closeExpiredEpisodes(now.minus(EPISODE_WINDOW), now);
    }

    /** 보관 기간 안에 분석한 영상 중 지금 볼 수 없게 된 영상(삭제·숨김·퇴장 멤버). */
    public Set<Long> findUnavailableAnalyzedVideos(Instant now) {
        List<Long> analyzed = pipeline.findRecentlyAnalyzedVideoIds(now.minus(properties.snapshot().retention()));
        return analyzed.isEmpty() ? Set.of() : videoPosts.findUnavailableIds(analyzed);
    }

    /**
     * 영상의 추천을 모두 막고, 새로 막은 것이 있으면 작업기가 참고 사례·벡터 등 파생 자료를 지우게 한다.
     * 숨김 → 해제 → 삭제처럼 여러 번 막혀도 매번 삭제 작업이 돌도록 키에 시각을 넣는다.
     */
    public void revokeVideo(Long videoId, Instant now) {
        if (pipeline.revokeVideo(videoId, VIDEO_UNAVAILABLE, now) > 0) {
            pipeline.enqueueJob("purge-video:" + videoId + ":" + now.getEpochSecond(), "PURGE_VIDEO",
                    AiReplyJson.write(Map.of("video_id", videoId, "reason", VIDEO_UNAVAILABLE)), 0, now);
        }
    }

    /** 원문 메시지가 지워진(운영자 삭제·퇴장 멤버) 답장의 원문 스냅샷을 지운다. */
    public int purgeDeletedReplies(Instant now) {
        List<Long> messageIds = feedback.findReplySnapshotMessageIds(now.minus(properties.snapshot().retention()));
        if (messageIds.isEmpty()) {
            return 0;
        }
        Set<Long> visible = chatMessages.findAllByIds(messageIds).stream()
                .filter(message -> !message.isAdminDeleted())
                .map(org.son.haruharu.domain.chat.implement.ChatMessage::id)
                .collect(java.util.stream.Collectors.toSet());
        return feedback.deleteReplySnapshots(messageIds.stream().filter(id -> !visible.contains(id)).toList());
    }

    public void purgeExpired(Instant now) {
        Instant before = now.minus(properties.snapshot().retention());
        pipeline.purgeSnapshotsBefore(before, now);
        feedback.purgeReplySnapshotsBefore(before);
    }
}
