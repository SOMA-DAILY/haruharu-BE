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
                NotificationType.REACTION_RECEIVED, Map.of("videoPostId", event.videoPostId().toString()),
                event.reactorNickname(), reactionVerb(event.reactionType()));
    }

    /** ReactionType.name() → 푸시 본문 동사(카피: "회원님의 영상을 %s"). 새 반응 유형 추가 시 여기도 함께 갱신한다. */
    private static String reactionVerb(String reactionType) {
        return switch (reactionType) {
            case "LIKE" -> "좋아해요";
            case "HEART" -> "마음에 들어해요";
            case "LOVE" -> "사랑해요";
            case "LAUGH" -> "재밌어해요";
            case "WOW" -> "놀라워해요";
            case "FIRE" -> "열광해요";
            default -> "확인했어요";
        };
    }
}
