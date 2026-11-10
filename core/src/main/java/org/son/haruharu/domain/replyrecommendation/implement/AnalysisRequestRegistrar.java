package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Component;

/** 분석 요청을 찾거나 만든다. 게시 트랜잭션(outbox)과 추천 요청이 함께 쓴다. */
@Component
@RequiredArgsConstructor
public class AnalysisRequestRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRequestRegistrar.class);

    private final AnalysisPipelineRepository pipeline;
    private final VideoPostReader videoPosts;
    private final RoomMemberReader roomMembers;
    private final SavepointRunner savepoints;

    public static String captionRevision(VideoPost post) {
        return AiReplyJson.sha256(post.caption() == null ? "" : post.caption());
    }

    public static String mediaRevision(VideoPost post) {
        return AiReplyJson.sha256(post.videoUrl());
    }

    public AnalysisTarget targetFor(VideoPost post, RoomMember owner, AccessScope scope, ActiveVersion version) {
        return new AnalysisTarget(post.id(), owner.roomId(), owner.userId(), scope, captionRevision(post),
                mediaRevision(post), version.preprocessVersion());
    }

    public Optional<AnalysisRequest> findActive(AnalysisTarget target) {
        return pipeline.findActiveAnalysisRequest(target);
    }

    public Optional<AnalysisRequest> find(Long id) {
        return pipeline.findAnalysisRequest(id);
    }

    public AnalysisRequest create(AnalysisTarget target, AnalysisOrigin origin, Instant now) {
        return pipeline.createAnalysisRequest(target, origin, now);
    }

    /**
     * 게시 트랜잭션 안에서 기본 범위(차단 없음) 분석 요청을 넣는다(outbox). ai_reply 쪽 SQL 은 savepoint 안에서만 돌아
     * 실패해도(스키마 미적용·권한·제약 위반 등) 그 부분만 되돌리고 게시는 그대로 저장된다. 빠진 분석은 추천 요청이 다시 만든다.
     */
    public void registerPublished(Long videoPostId, Long roomMemberId, Instant now) {
        VideoPost post = videoPosts.getById(videoPostId);
        RoomMember owner = roomMembers.getById(roomMemberId);
        boolean registered = savepoints.runIsolated(() -> {
            if (!pipeline.schemaReady()) {
                return;
            }
            pipeline.findActiveVersion().ifPresent(version -> pipeline.createAnalysisRequest(
                    targetFor(post, owner, AccessScope.base(), version), AnalysisOrigin.PUBLISH, now));
        });
        if (!registered) {
            log.atWarn().addKeyValue("event", "REPLY_RECOMMENDATION_OUTBOX_SKIPPED")
                    .addKeyValue("videoPostId", videoPostId)
                    .log("게시 분석 요청을 넣지 못함(게시는 저장됨, 추천 요청 때 다시 만든다)");
        }
    }
}
