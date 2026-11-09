package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 사건·episode·답장 연결·답장 원문 스냅샷(ai_reply 스키마). */
public interface RecommendationFeedbackRepository {

    /** 새 사건이면 true. 같은 event_id 가 이미 있으면 false. */
    boolean insertEvent(RecommendationEvent event);

    Optional<String> findEventPayloadSha(String eventId);

    /** 사건으로 episode 를 만들거나 노출 시각·첫 선택·입력 시작을 갱신한다(닫힌 episode 는 그대로). */
    void touchEpisode(RecommendationEvent event, Long roomId, Instant now);

    /** 답장 시각 이전 24시간 안에 첫 노출이 있었던 열린 episode. */
    List<ReplyEpisode> findOpenEpisodes(Long viewerId, Long videoId, Instant notBefore, Instant notAfter);

    void closeEpisode(Long episodeId, Long messageId, Instant repliedAt, ReplyAttribution attribution, Instant now);

    boolean insertReplyLink(ReplyLink link, Instant now);

    void saveReplySnapshot(Long episodeId, Long messageId, String text, Provenance provenance, Instant now);

    /** 첫 노출 뒤 24시간 동안 답장이 없던 episode 를 NO_REPLY 로 닫는다. */
    int closeExpiredEpisodes(Instant openedBefore, Instant now);

    int purgeReplySnapshotsBefore(Instant before);

    /** 답장 원문 스냅샷이 가리키는 메시지 id(보관 기간 안). 원문이 지워진 답장을 찾는 데 쓴다. */
    java.util.List<Long> findReplySnapshotMessageIds(Instant since);

    int deleteReplySnapshots(java.util.Collection<Long> messageIds);
}
