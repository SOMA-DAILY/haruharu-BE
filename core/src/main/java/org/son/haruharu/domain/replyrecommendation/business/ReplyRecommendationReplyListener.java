package org.son.haruharu.domain.replyrecommendation.business;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.chat.business.event.VideoReplySavedEvent;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyLinkRecorder;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyRecommendationProperties;
import org.son.haruharu.domain.replyrecommendation.implement.SavedVideoReply;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 답장이 커밋된 뒤 추천 노출과 잇는다. 별도 트랜잭션이라 여기서 실패해도 이미 저장된 답장에는 영향이 없다.
 * 로그에는 id 만 남긴다(답장 본문 없음).
 */
@Component
@RequiredArgsConstructor
public class ReplyRecommendationReplyListener {

    private static final Logger log = LoggerFactory.getLogger(ReplyRecommendationReplyListener.class);

    private final ReplyRecommendationProperties properties;
    private final ReplyLinkRecorder recorder;
    private final org.son.haruharu.domain.replyrecommendation.implement.RecommendationCollectionPolicy collection;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReplySaved(VideoReplySavedEvent event) {
        if (!properties.enabledFor(event.roomId()) || !collection.collectable(event.senderId())) {
            return; // 남길 자료가 없으면 새 트랜잭션(두 번째 연결)도 열지 않는다
        }
        SavedVideoReply reply = new SavedVideoReply(event.messageId(), event.senderId(), event.videoPostId(),
                event.sentAt(), event.sourceSetId(), event.sourceCardId(), event.sourceSessionId());
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.executeWithoutResult(ignored -> recorder.record(reply, clock.instant()));
        } catch (RuntimeException failure) {
            log.atWarn()
                    .setCause(failure)
                    .addKeyValue("event", "REPLY_RECOMMENDATION_LINK_FAILED")
                    .addKeyValue("messageId", event.messageId())
                    .log("영상 답장과 추천 연결 실패(답장은 저장됨)");
        }
    }
}
