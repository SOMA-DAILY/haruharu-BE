package org.son.haruharu.domain.notification.business;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.question.business.QuestionService;
import org.son.haruharu.domain.question.business.dto.QuestionDeadlineTargets;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class QuestionDeadlineNotifierTest {
    final LocalDateTime now = LocalDateTime.of(2026, 9, 14, 16, 0);

    @Mock RoomReader roomReader;
    @Mock QuestionService questionService;
    @Mock PushSendService pushSendService;
    QuestionDeadlineNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new QuestionDeadlineNotifier(roomReader, questionService, pushSendService);
    }

    private Room room(RoomStatus status, LocalDateTime closesAt) {
        return new Room(7L, status, now.toLocalDate(), 3, now.minusHours(30), null,
                now.minusHours(30), closesAt, closesAt.plusHours(1), closesAt.plusHours(2),
                closesAt.plusHours(3));
    }

    @Test
    @DisplayName("마감 2시간 전에는 미답변자와 질문권이 남은 사람에게 서로 다른 안내를 보낸다")
    void givenTwoHoursLeft_whenNotify_thenSendsSeparateReminders() {
        given(roomReader.getReconcilable()).willReturn(List.of(room(RoomStatus.ACTIVE, now.plusHours(2))));
        given(questionService.deadlineTargets(7L))
                .willReturn(new QuestionDeadlineTargets(List.of(1L, 2L), List.of(3L)));

        assertThat(notifier.notifyClosingQuestions(now)).isEqualTo(3);
        then(pushSendService).should().sendToUsers(List.of(1L, 2L),
                NotificationType.QUESTION_ANSWER_DEADLINE_SOON, Map.of("room_id", "7"));
        then(pushSendService).should().sendToUsers(List.of(3L),
                NotificationType.QUESTION_SEND_DEADLINE_SOON, Map.of("room_id", "7"));
    }

    @Test
    @DisplayName("마감 시각에 초가 남아 있어도 분 단위로 맞춰 한 번 보낸다")
    void givenDeadlineWithSeconds_whenNotify_thenStillMatchesTheMinute() {
        given(roomReader.getReconcilable())
                .willReturn(List.of(room(RoomStatus.ACTIVE, now.plusHours(2).plusSeconds(37))));
        given(questionService.deadlineTargets(7L))
                .willReturn(new QuestionDeadlineTargets(List.of(1L), List.of()));

        assertThat(notifier.notifyClosingQuestions(now)).isEqualTo(1);
        then(pushSendService).should().sendToUsers(List.of(1L),
                NotificationType.QUESTION_ANSWER_DEADLINE_SOON, Map.of("room_id", "7"));
    }

    @Test
    @DisplayName("마감 2시간 전이 아니면 대상을 조회하지도 않는다")
    void givenOtherMinute_whenNotify_thenSkipsRoomEntirely() {
        given(roomReader.getReconcilable())
                .willReturn(List.of(room(RoomStatus.ACTIVE, now.plusHours(2).plusMinutes(1))));

        assertThat(notifier.notifyClosingQuestions(now)).isZero();
        then(questionService).should(never()).deadlineTargets(7L);
        then(pushSendService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("최종선택이 이미 열린 방과 시작 전 방은 마감 안내 대상이 아니다")
    void givenNotActiveRoom_whenNotify_thenSendsNothing() {
        given(roomReader.getReconcilable()).willReturn(List.of(
                room(RoomStatus.ACTIVE, now.minusHours(1)),
                new Room(8L, RoomStatus.PENDING, now.toLocalDate(), 3, null, null,
                        now.plusHours(1), now.plusHours(2), now.plusHours(3), now.plusHours(4),
                        now.plusHours(5))));

        assertThat(notifier.notifyClosingQuestions(now)).isZero();
        then(pushSendService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("보낼 사람이 없으면 빈 발송을 만들지 않는다")
    void givenNoTargets_whenNotify_thenSendsNothing() {
        given(roomReader.getReconcilable()).willReturn(List.of(room(RoomStatus.ACTIVE, now.plusHours(2))));
        given(questionService.deadlineTargets(7L))
                .willReturn(new QuestionDeadlineTargets(List.of(), List.of()));

        assertThat(notifier.notifyClosingQuestions(now)).isZero();
        then(pushSendService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("방 목록 조회가 실패하면 아무것도 보내지 않은 채 그대로 알린다")
    void givenRoomLookupFailure_whenNotify_thenThrowsBeforeSendingAnything() {
        given(roomReader.getReconcilable()).willThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> notifier.notifyClosingQuestions(now))
                .isInstanceOf(IllegalStateException.class);
        then(pushSendService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("한 방의 조회 실패가 다른 방의 안내를 막지 않는다")
    void givenOneFailingRoom_whenNotify_thenKeepsGoing() {
        Room failing = room(RoomStatus.ACTIVE, now.plusHours(2));
        Room healthy = new Room(9L, RoomStatus.ACTIVE, now.toLocalDate(), 3, now.minusHours(30), null,
                now.minusHours(30), now.plusHours(2), now.plusHours(3), now.plusHours(4), now.plusHours(5));
        given(roomReader.getReconcilable()).willReturn(List.of(failing, healthy));
        given(questionService.deadlineTargets(7L)).willThrow(new IllegalStateException("unavailable"));
        given(questionService.deadlineTargets(9L))
                .willReturn(new QuestionDeadlineTargets(List.of(5L), List.of()));

        assertThat(notifier.notifyClosingQuestions(now)).isEqualTo(1);
        then(pushSendService).should().sendToUsers(List.of(5L),
                NotificationType.QUESTION_ANSWER_DEADLINE_SOON, Map.of("room_id", "9"));
    }
}
