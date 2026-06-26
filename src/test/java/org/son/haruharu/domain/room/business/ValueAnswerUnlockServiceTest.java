package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.UnlockResponse;
import org.son.haruharu.domain.room.business.exception.InsufficientKeysException;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.value.implement.UserValueAnswer;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.ValueAnswerUnlock;
import org.son.haruharu.domain.value.implement.ValueAnswerUnlockReader;
import org.son.haruharu.domain.value.implement.ValueAnswerUnlockWriter;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ValueAnswerUnlockServiceTest {

    @Mock RoomMemberReader roomMemberReader;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock UserValueAnswerReader userValueAnswerReader;
    @Mock ValueQuestionReader valueQuestionReader;
    @Mock ValueAnswerUnlockReader valueAnswerUnlockReader;
    @Mock ValueAnswerUnlockWriter valueAnswerUnlockWriter;

    ValueAnswerUnlockService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final UUID roomId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final UUID meId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final UUID answerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ValueAnswerUnlockService(roomMemberReader, roomMemberWriter, userValueAnswerReader,
                valueQuestionReader, valueAnswerUnlockReader, valueAnswerUnlockWriter, clock);
    }

    private RoomMember me(int pointSum) {
        return new RoomMember(meId, roomId, userId, "MALE", pointSum, LocalDateTime.now());
    }

    private RoomMember target() {
        return new RoomMember(targetId, roomId, targetUserId, "FEMALE", 0, LocalDateTime.now());
    }

    private void stubLookups(int myPoints) {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me(myPoints));
        given(roomMemberReader.getById(targetId)).willReturn(target());
        given(userValueAnswerReader.getById(answerId))
                .willReturn(new UserValueAnswer(answerId, targetUserId, questionId, "다정한 사람"));
    }

    @Test
    @DisplayName("열쇠가 충분하고 미해금이면 unlock_cost만큼 차감하고 답변을 해금한다")
    void givenEnoughKeys_whenUnlock_thenChargesAndUnlocks() {
        stubLookups(30);
        given(valueAnswerUnlockReader.isUnlocked(meId, answerId)).willReturn(false);
        given(valueQuestionReader.getById(questionId))
                .willReturn(new ValueQuestion(questionId, "이상형은?", "LOVE", 1, true, 20));
        given(roomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        UnlockResponse result = service.unlock(roomId, targetId, answerId, userId);

        assertThat(result.answer()).isEqualTo("다정한 사람");
        assertThat(result.remainingPointSum()).isEqualTo(10);
        then(valueAnswerUnlockWriter).should().save(any(ValueAnswerUnlock.class));
    }

    @Test
    @DisplayName("열쇠가 부족하면 InsufficientKeysException이 발생하고 차감·해금이 일어나지 않는다")
    void givenNotEnoughKeys_whenUnlock_thenThrowsAndNoCharge() {
        stubLookups(5);
        given(valueAnswerUnlockReader.isUnlocked(meId, answerId)).willReturn(false);
        given(valueQuestionReader.getById(questionId))
                .willReturn(new ValueQuestion(questionId, "이상형은?", "LOVE", 1, true, 20));

        assertThatThrownBy(() -> service.unlock(roomId, targetId, answerId, userId))
                .isInstanceOf(InsufficientKeysException.class);

        then(roomMemberWriter).should(never()).save(any());
        then(valueAnswerUnlockWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 해금한 답변은 재차감 없이 멱등하게 현재 상태를 반환한다")
    void givenAlreadyUnlocked_whenUnlock_thenIdempotentNoCharge() {
        stubLookups(30);
        given(valueAnswerUnlockReader.isUnlocked(meId, answerId)).willReturn(true);

        UnlockResponse result = service.unlock(roomId, targetId, answerId, userId);

        assertThat(result.answer()).isEqualTo("다정한 사람");
        assertThat(result.remainingPointSum()).isEqualTo(30);
        then(roomMemberWriter).should(never()).save(any());
        then(valueAnswerUnlockWriter).should(never()).save(any());
    }
}
