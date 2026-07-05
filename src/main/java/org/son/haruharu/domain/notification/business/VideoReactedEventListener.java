package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.video.business.event.VideoReactedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Component
public class VideoReactedEventListener {

    private final PushSendService pushSendService;

    public VideoReactedEventListener(PushSendService pushSendService) {
        this.pushSendService = pushSendService;
    }

    // 커밋 후 발송 — 반응 토글 트랜잭션이 푸시 실패로 롤백되지 않도록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(VideoReactedEvent event) {
        pushSendService.sendToUsers(List.of(event.authorUserId()),
                NotificationType.REACTION_RECEIVED, Map.of("videoPostId", event.videoPostId().toString()));
    }
}