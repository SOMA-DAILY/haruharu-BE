package org.son.haruharu.domain.notification.business;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.question.business.QuestionService;
import org.son.haruharu.domain.question.implement.event.QuestionAnsweredEvent;
import org.son.haruharu.domain.question.implement.event.QuestionCreatedEvent;
import org.son.haruharu.domain.question.implement.event.QuestionGrantAvailableEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 저장이 확정된 소식만 발송하며, 푸시와 알림함에는 신원·본문을 싣지 않는다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuestionNotificationEventListener {
    private final PushSendService pushSendService;
    private final QuestionService questionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(QuestionCreatedEvent event) {
        safely(() -> {
            if (questionService.canNotify(event.roomId(), event.questionId())) {
                pushSendService.sendToUsers(List.of(event.recipientUserId()), NotificationType.QUESTION_RECEIVED,
                        payload(event.roomId(), event.questionId()));
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnswered(QuestionAnsweredEvent event) {
        safely(() -> {
            if (questionService.canNotify(event.roomId(), event.questionId())) {
                pushSendService.sendToUsers(List.of(event.senderUserId()), NotificationType.QUESTION_ANSWERED,
                        payload(event.roomId(), event.questionId()));
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGrant(QuestionGrantAvailableEvent event) {
        safely(() -> {
            if (questionService.canNotifyGrant(event.roomId(), event.userId())) {
                pushSendService.sendToUsers(List.of(event.userId()), NotificationType.QUESTION_AVAILABLE,
                        Map.of("room_id", event.roomId().toString()));
            }
        });
    }

    private void safely(Runnable notification) {
        try {
            notification.run();
        } catch (Exception exception) {
            // 저장은 이미 커밋됐다. 실패 원문에는 식별자가 포함될 수 있어 예외 종류만 기록한다.
            log.warn("익명 질문 알림 처리 실패: {}", exception.getClass().getSimpleName());
        }
    }

    private static Map<String, String> payload(Long roomId, Long questionId) {
        return Map.of("room_id", roomId.toString(), "question_id", questionId.toString());
    }
}
