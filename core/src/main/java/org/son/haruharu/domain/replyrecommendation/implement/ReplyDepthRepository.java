package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Depth 라벨 수집(#559): 답장 연결·채팅 답글 읽기와 ai_reply.reply_depth_observation 쓰기. */
public interface ReplyDepthRepository {

    /** 559 표가 있는지. 없으면 수집하지 않는다. */
    boolean schemaReady();

    /** {@code sentBefore} 이전에 보낸, 아직 라벨이 없는 실제 영상 답장(보낸 순서). */
    List<ReplyDepthTarget> findDueTargets(Instant sentBefore, int limit);

    /** 답장 메시지. 숨겨진 메시지도 돌려준다(excluded). 행이 없으면 빈 값. */
    Optional<ReplyDepthRoot> findRoot(Long messageId);

    /** 같은 채팅방에서 답장 뒤 {@code windowEnd} 까지 저장된 답글 메시지(숨겨진 것 포함). */
    List<ReplyDepthMessage> findRepliesAfter(Long roomId, Long rootMessageId, LocalDateTime windowEnd);

    /** 영상 작성자 회원 id(지워진 영상 포함). 영상이 없으면 빈 값. */
    Optional<Long> findVideoAuthor(Long videoId);

    /** 같은 채팅방에서 답장 뒤 {@code windowEnd} 까지 {@code senderIds} 가 보낸 숨겨지지 않은 메시지 수(답장 자체 제외). */
    int countMessagesAfter(Long roomId, Long rootMessageId, LocalDateTime windowEnd, java.util.Collection<Long> senderIds);

    /** 새로 넣었으면 true. 이미 있으면 그대로 두고 false. */
    boolean insert(ReplyDepthObservation observation);
}
