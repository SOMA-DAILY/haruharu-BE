package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse.ValueAnswerView;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.son.haruharu.domain.value.implement.UserValueAnswer;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.ValueAnswerUnlockReader;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 방 멤버 단건 프로필 조회. 기본 프로필은 Day3 게이트, 가치관 답변은 열쇠 해금 게이트를 적용한다.
 * room·profile·value 세 도메인의 Reader를 조율한다(같은 방 멤버 한정).
 */
@Service
@Transactional(readOnly = true)
public class MemberProfileService {

    /** 직업/학교·키·MBTI 등 기본 프로필이 공개되는 Day(와이어 [Day3] 게이트). RoomMembersService와 동일 기준. */
    private static final int PROFILE_REVEAL_DAY = 3;

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final UserProfileReader userProfileReader;
    private final ValueQuestionReader valueQuestionReader;
    private final UserValueAnswerReader userValueAnswerReader;
    private final ValueAnswerUnlockReader valueAnswerUnlockReader;
    private final Clock clock;

    public MemberProfileService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                UserProfileReader userProfileReader, ValueQuestionReader valueQuestionReader,
                                UserValueAnswerReader userValueAnswerReader,
                                ValueAnswerUnlockReader valueAnswerUnlockReader, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.userProfileReader = userProfileReader;
        this.valueQuestionReader = valueQuestionReader;
        this.userValueAnswerReader = userValueAnswerReader;
        this.valueAnswerUnlockReader = valueAnswerUnlockReader;
        this.clock = clock;
    }

    public MemberProfileResponse getMemberProfile(UUID roomId, UUID memberId, UUID userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        RoomMember target = roomMemberReader.getById(memberId);
        if (!target.roomId().equals(roomId)) {
            throw new RoomMemberNotFoundException(); // 다른 방 멤버는 이 방에서 조회 불가
        }
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);

        boolean revealed = room.currentDay() >= PROFILE_REVEAL_DAY;
        boolean self = target.userId().equals(userId);

        UserProfile profile = userProfileReader.findByUserId(target.userId()).orElse(null);
        Integer age = (profile == null || profile.birthday() == null)
                ? null : Period.between(profile.birthday(), LocalDate.now(clock)).getYears();

        List<ValueAnswerView> valueAnswers = toValueAnswers(target.userId(), me.id(), self);

        return new MemberProfileResponse(
                target.id(),
                profile == null ? null : profile.nickname(),
                target.gender(),
                profile == null ? null : profile.location(),
                age,
                revealed && profile != null ? profile.affiliation() : null,
                revealed && profile != null ? profile.height() : null,
                revealed && profile != null ? profile.mbti() : null,
                valueAnswers);
    }

    private List<ValueAnswerView> toValueAnswers(UUID targetUserId, UUID myRoomMemberId, boolean self) {
        Map<UUID, ValueQuestion> questions = valueQuestionReader.getAllActive().stream()
                .collect(Collectors.toMap(ValueQuestion::id, Function.identity()));
        Set<UUID> unlockedIds = Set.copyOf(valueAnswerUnlockReader.getUnlockedAnswerIds(myRoomMemberId));

        return userValueAnswerReader.getAllByUserId(targetUserId).stream()
                .filter(answer -> questions.containsKey(answer.questionId()))
                .map(answer -> toView(answer, questions.get(answer.questionId()), unlockedIds, self))
                .toList();
    }

    private ValueAnswerView toView(UserValueAnswer answer, ValueQuestion question,
                                   Set<UUID> unlockedIds, boolean self) {
        boolean unlocked = self || unlockedIds.contains(answer.id());
        return new ValueAnswerView(
                answer.id(),
                question.id(),
                question.questionText(),
                question.unlockCost(),
                unlocked,
                unlocked ? answer.answer() : null);
    }
}
