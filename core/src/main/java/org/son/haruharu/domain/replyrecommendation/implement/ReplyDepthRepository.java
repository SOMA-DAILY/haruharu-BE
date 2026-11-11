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

    /** 효과 라벨 칸(561 SQL, 이슈 #560)이 있는지. 없으면 효과 라벨 없이 넣는다. */
    boolean responseSchemaReady();

    /**
     * 같은 채팅방에서 답장 뒤(id 와 시각 모두 답장 이후) {@code windowEnd} 까지(포함) {@code senderId} 가 보낸
     * 첫 숨겨지지 않은 메시지 시각.
     */
    Optional<LocalDateTime> findFirstMessageAt(Long roomId, Long rootMessageId, LocalDateTime rootAt,
                                               LocalDateTime windowEnd, Long senderId);

    /**
     * 대조 확인용. 채팅방의 3일 방에서 답장 시각({@code rootAt})에 멤버였던 사람 중 {@code excluded} 를 뺀 수와,
     * 그중 답장 뒤 {@code windowEnd} 까지(포함) 같은 채팅방에 숨겨지지 않은 메시지를 보낸 수.
     */
    ReplyBystanders countBystanders(Long roomId, Long rootMessageId, LocalDateTime rootAt, LocalDateTime windowEnd,
                                    java.util.Collection<Long> excluded);

    /** 새로 넣었으면 true. 이미 있으면 그대로 두고 false. */
    boolean insert(ReplyDepthObservation observation);
}
