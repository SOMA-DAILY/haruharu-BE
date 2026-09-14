package org.son.haruharu.domain.notification.business;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.implement.NotificationCategory;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.question.business.QuestionService;
import org.son.haruharu.domain.question.implement.event.QuestionAnsweredEvent;
import org.son.haruharu.domain.question.implement.event.QuestionCreatedEvent;
import org.son.haruharu.domain.question.implement.event.QuestionGrantAvailableEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class QuestionNotificationEventListenerTest {
    @Mock PushSendService pushSendService;
    @Mock QuestionService questionService;
    QuestionNotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new QuestionNotificationEventListener(pushSendService, questionService);
    }

    @Test
    @DisplayName("질문 도착은 수신자에게만 방·질문 식별자로 안내하고 발신자와 본문은 전달하지 않는다")
    void givenQuestion_whenCreated_thenOnlyRecipientGetsPrivatePayload() {
        given(questionService.canNotify(7L, 11L)).willReturn(true);
        listener.onCreated(new QuestionCreatedEvent(7L, 11L, 1L, 2L));
        then(pushSendService).should().sendToUsers(List.of(2L), NotificationType.QUESTION_RECEIVED,
                Map.of("room_id", "7", "question_id", "11"));
    }

    @Test
    @DisplayName("답변 도착은 질문자에게만 해당 답변 화면으로 안내한다")
    void givenAnswer_whenAnswered_thenOnlySenderGetsAnswerLink() {
        given(questionService.canNotify(7L, 11L)).willReturn(true);
        listener.onAnswered(new QuestionAnsweredEvent(7L, 11L, 1L, 2L));
        then(pushSendService).should().sendToUsers(List.of(1L), NotificationType.QUESTION_ANSWERED,
                Map.of("room_id", "7", "question_id", "11"));
    }

    @Test
    @DisplayName("질문권 지급 알림에는 방 식별자만 포함한다")
    void givenGrant_whenAvailable_thenSendsRoomLink() {
        given(questionService.canNotifyGrant(7L, 1L)).willReturn(true);
        listener.onGrant(new QuestionGrantAvailableEvent(7L, 1L));
        then(pushSendService).should().sendToUsers(List.of(1L), NotificationType.QUESTION_AVAILABLE,
                Map.of("room_id", "7"));
    }

    @Test
    @DisplayName("커밋 후 차단·신고·이탈로 접근 자격을 잃으면 질문과 답변 알림을 보내지 않는다")
    void givenIneligibleExchange_whenEventsArrive_thenNoNotification() {
        listener.onCreated(new QuestionCreatedEvent(7L, 11L, 1L, 2L));
        listener.onAnswered(new QuestionAnsweredEvent(7L, 11L, 1L, 2L));
        listener.onGrant(new QuestionGrantAvailableEvent(7L, 1L));
        then(pushSendService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("알림 자격 재조회 실패는 저장된 질문 결과를 실패로 바꾸지 않으며 알림은 보내지 않는다")
    void givenEligibilityFailure_whenCreated_thenPreservesCommittedResult() {
        given(questionService.canNotify(7L, 11L)).willThrow(new IllegalStateException("unavailable"));
        assertThatCode(() -> listener.onCreated(new QuestionCreatedEvent(7L, 11L, 1L, 2L)))
                .doesNotThrowAnyException();
        then(pushSendService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("익명 질문 알림은 모두 활동 수신 설정을 따르고 알림함에 저장된다")
    void givenQuestionTypes_whenInspect_thenActivityPreferenceAndSafeFixedCopy() {
        for (var type : List.of(NotificationType.QUESTION_AVAILABLE,
                NotificationType.QUESTION_RECEIVED, NotificationType.QUESTION_ANSWERED)) {
            assertThat(type.category()).isEqualTo(NotificationCategory.ACTIVITY);
            assertThat(type.inboxVisible()).isTrue();
            assertThat(type.title() + type.body()).doesNotContain("%s", "%1$s");
        }
    }
}
