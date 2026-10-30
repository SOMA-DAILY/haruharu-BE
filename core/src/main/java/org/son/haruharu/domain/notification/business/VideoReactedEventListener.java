package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.video.business.event.VideoReactedEvent;
import org.son.haruharu.domain.video.implement.ReactionType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Component
public class VideoReactedEventListener {

    private final PushSendService pushSendService;
    private final BlockReader blockReader;

    private final RoomReactionDeliveryService delivery;

    public VideoReactedEventListener(PushSendService pushSendService, BlockReader blockReader, RoomReactionDeliveryService delivery) {
        this.pushSendService = pushSendService;
        this.blockReader = blockReader;
        this.delivery = delivery;
    }

    // 커밋 후 발송 — 반응 토글 트랜잭션이 푸시 실패로 롤백되지 않도록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(VideoReactedEvent event) {
        if (!delivery.canDeliver(event.videoPostId(), event.authorUserId(), event.reactorUserId())) {
            return;
        }
        // 작성자가 반응자를 차단했으면 알림을 억제한다(#408 — 반응 자체는 허용, 알림만 차단).
        if (blockReader.hasBlocked(event.authorUserId(), event.reactorUserId())) {
            return;
        }
        pushSendService.sendToUsers(List.of(event.authorUserId()),
                NotificationType.REACTION_RECEIVED, Map.of("videoPostId", event.videoPostId().toString(), "reactor_user_id", event.reactorUserId().toString()),
                event.reactorNickname(), reactionVerb(event.reactionType()), reactionEmoji(event.reactionType()));
    }

    /** 푸시 제목 앞 이모지(#446). 알 수 없는 유형(과거 값 등)이면 기존 기본값 👀. */
    private static String reactionEmoji(String reactionType) {
        String emoji = ReactionType.emojiOf(reactionType);
        return emoji == null ? "👀" : emoji;
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
