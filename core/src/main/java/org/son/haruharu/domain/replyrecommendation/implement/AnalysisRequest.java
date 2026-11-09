package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;
import java.util.List;

/** 영상 한 편·시청 범위 하나의 분석 요청. 게시 때(PUBLISH) 또는 추천 요청 때(ENSURE) 만든다. */
public record AnalysisRequest(
        Long id,
        Long videoId,
        Long roomId,
        Long authorId,
        String scopeHash,
        List<Long> excludedActorIds,
        String captionRevision,
        String mediaRevision,
        String preprocessVersion,
        int revisionNo,
        AnalysisOrigin origin,
        AnalysisRequestStatus status,
        Long analysisInputId,
        int attemptCount,
        Instant createdAt,
        Instant updatedAt
) {
    public AccessScope scope() {
        return new AccessScope(excludedActorIds, scopeHash);
    }

    public boolean matches(AnalysisTarget target) {
        return videoId.equals(target.videoId())
                && scopeHash.equals(target.scope().hash())
                && captionRevision.equals(target.captionRevision())
                && mediaRevision.equals(target.mediaRevision())
                && preprocessVersion.equals(target.preprocessVersion());
    }

    public String analysisJobKey() {
        return analysisJobKey(analysisInputId);
    }

    public static String analysisJobKey(Long analysisInputId) {
        return "analysis:" + analysisInputId;
    }

    public AnalysisTarget target() {
        return new AnalysisTarget(videoId, roomId, authorId, scope(), captionRevision, mediaRevision, preprocessVersion);
    }
}
