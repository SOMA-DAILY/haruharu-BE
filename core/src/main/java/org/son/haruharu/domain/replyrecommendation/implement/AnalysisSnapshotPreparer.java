package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException;
import org.springframework.stereotype.Component;

/**
 * 대기 중인 분석 요청 하나를 입력 스냅샷으로 만든다. 요청을 만든 뒤 영상이 지워지거나 캡션이 바뀌었으면
 * 요청을 취소한다(새 값으로는 다음 추천 요청이 새 키를 만든다).
 */
@Component
@RequiredArgsConstructor
public class AnalysisSnapshotPreparer {

    /** 시청자가 기다리는 요청을 게시 시 미리 만드는 요청보다 먼저 처리한다. */
    public static final int PRIORITY_VIEWER = 10;
    public static final int PRIORITY_PUBLISH = 0;

    private final AnalysisPipelineRepository pipeline;
    private final VideoPostReader videoPosts;
    private final ChatRoomReader chatRooms;
    private final RoomHistoryCollector collector;
    private final Clock clock;

    public void prepare(Long analysisRequestId, Instant now) {
        Optional<AnalysisRequest> found = pipeline.findAnalysisRequest(analysisRequestId);
        if (found.isEmpty() || found.get().status() != AnalysisRequestStatus.SNAPSHOT_PENDING) {
            return;
        }
        AnalysisRequest request = found.get();
        VideoPost post;
        try {
            post = videoPosts.getById(request.videoId());
        } catch (VideoPostNotFoundException e) {
            pipeline.cancelAnalysisRequest(request.id(), "VIDEO_UNAVAILABLE", now);
            return;
        }
        if (post.deletedAt() != null || post.isHidden()) {
            pipeline.cancelAnalysisRequest(request.id(), "VIDEO_UNAVAILABLE", now);
            return;
        }
        if (!AnalysisRequestRegistrar.captionRevision(post).equals(request.captionRevision())) {
            pipeline.cancelAnalysisRequest(request.id(), "CAPTION_CHANGED", now);
            return;
        }
        if (!AnalysisRequestRegistrar.mediaRevision(post).equals(request.mediaRevision())) {
            pipeline.cancelAnalysisRequest(request.id(), "MEDIA_CHANGED", now);
            return;
        }
        Optional<ChatRoom> chat = chatRooms.findByRoomId(request.roomId());
        HistorySnapshot history = chat
                .map(room -> collector.collect(request.roomId(), room.id(), post, request.scope()))
                .orElseGet(HistorySnapshot::unavailable);
        AnalysisInputDraft draft = new AnalysisInputDraft(request,
                AiReplyJson.iso(post.createdAt(), clock.getZone()),
                post.caption() == null ? "" : post.caption(), post.videoUrl(), history);
        int priority = request.origin() == AnalysisOrigin.ENSURE ? PRIORITY_VIEWER : PRIORITY_PUBLISH;
        Long inputId = pipeline.insertAnalysisInput(draft, now);
        pipeline.insertEvidence(EvidenceOwner.ANALYSIS, inputId, history.records());
        pipeline.enqueueJob(AnalysisRequest.analysisJobKey(inputId), "ANALYSIS",
                AiReplyJson.write(java.util.Map.of("analysis_input_id", inputId)), priority, now);
        pipeline.markSnapshotReady(request.id(), inputId, now);
    }
}
