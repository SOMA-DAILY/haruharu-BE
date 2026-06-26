package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.room.business.dto.UnlockResponse;
import org.son.haruharu.domain.room.business.exception.InsufficientKeysException;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.son.haruharu.domain.value.implement.UserValueAnswer;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.ValueAnswerUnlock;
import org.son.haruharu.domain.value.implement.ValueAnswerUnlockReader;
import org.son.haruharu.domain.value.implement.ValueAnswerUnlockWriter;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;
import org.son.haruharu.domain.value.storage.exception.UserValueAnswerNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 가치관 답변 해금. 내 열쇠(room_members.point_sum)를 unlock_cost만큼 차감하고
 * 상대 답변 해금을 기록한다. 차감과 기록은 한 트랜잭션, 이미 해금된 답변은 멱등 처리(재차감 없음).
 */
@Service
@Transactional(readOnly = true)
public class ValueAnswerUnlockService {

    private final RoomMemberReader roomMemberReader;
    private final RoomMemberWriter roomMemberWriter;
    private final UserValueAnswerReader userValueAnswerReader;
    private final ValueQuestionReader valueQuestionReader;
    private final ValueAnswerUnlockReader valueAnswerUnlockReader;
    private final ValueAnswerUnlockWriter valueAnswerUnlockWriter;
    private final Clock clock;

    public ValueAnswerUnlockService(RoomMemberReader roomMemberReader, RoomMemberWriter roomMemberWriter,
                                    UserValueAnswerReader userValueAnswerReader, ValueQuestionReader valueQuestionReader,
                                    ValueAnswerUnlockReader valueAnswerUnlockReader,
                                    ValueAnswerUnlockWriter valueAnswerUnlockWriter, Clock clock) {
        this.roomMemberReader = roomMemberReader;
        this.roomMemberWriter = roomMemberWriter;
        this.userValueAnswerReader = userValueAnswerReader;
        this.valueQuestionReader = valueQuestionReader;
        this.valueAnswerUnlockReader = valueAnswerUnlockReader;
        this.valueAnswerUnlockWriter = valueAnswerUnlockWriter;
        this.clock = clock;
    }

    @Transactional
    public UnlockResponse unlock(UUID roomId, UUID memberId, UUID answerId, UUID userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        RoomMember target = roomMemberReader.getById(memberId);
        if (!target.roomId().equals(roomId)) {
            throw new RoomMemberNotFoundException(); // 다른 방 멤버 대상 불가
        }

        UserValueAnswer answer = userValueAnswerReader.getById(answerId);
        if (!answer.userId().equals(target.userId())) {
            throw new UserValueAnswerNotFoundException(); // 해당 멤버의 답변이 아님
        }

        // 멱등: 이미 해금했으면 재차감 없이 현재 상태를 그대로 반환
        if (valueAnswerUnlockReader.isUnlocked(me.id(), answerId)) {
            return new UnlockResponse(answerId, answer.answer(), me.pointSum());
        }

        ValueQuestion question = valueQuestionReader.getById(answer.questionId());
        if (me.pointSum() < question.unlockCost()) {
            throw new InsufficientKeysException();
        }

        RoomMember updated = roomMemberWriter.save(me.addPoints(-question.unlockCost()));
        valueAnswerUnlockWriter.save(ValueAnswerUnlock.of(me.id(), answerId, LocalDateTime.now(clock)));

        return new UnlockResponse(answerId, answer.answer(), updated.pointSum());
    }
}
