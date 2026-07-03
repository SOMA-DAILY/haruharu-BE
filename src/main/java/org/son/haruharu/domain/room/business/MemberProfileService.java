package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.profile.implement.ProfileField;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse.ProfileFieldView;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse.ValueAnswerView;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방 멤버 단건 프로필 조회. 항목별 공개는 currentDay 기반 자동 공개(ProfileField의 공개Day). 재화·해금 호출이 없다.
 * room·profile·value 세 도메인의 Reader를 조율한다(같은 방 멤버 한정).
 */
@Service
@Transactional(readOnly = true)
public class MemberProfileService {

    /** 나이 공개 Day. 나이는 UserProfile이 아닌 본인인증 스냅샷(RoomMember.birthday)에서 파생한다. */
    private static final int AGE_REVEAL_DAY = 3;
    /** 가치관 답변 공개 Day. */
    private static final int VALUE_REVEAL_DAY = 2;

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final UserProfileReader userProfileReader;
    private final ValueQuestionReader valueQuestionReader;
    private final UserValueAnswerReader userValueAnswerReader;
    private final Clock clock;

    public MemberProfileService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                UserProfileReader userProfileReader, ValueQuestionReader valueQuestionReader,
                                UserValueAnswerReader userValueAnswerReader, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.userProfileReader = userProfileReader;
        this.valueQuestionReader = valueQuestionReader;
        this.userValueAnswerReader = userValueAnswerReader;
        this.clock = clock;
    }

    public MemberProfileResponse getMemberProfile(UUID roomId, UUID memberId, Long userId) {
        roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        RoomMember target = roomMemberReader.getById(memberId);
        if (!target.roomId().equals(roomId)) {
            throw new RoomMemberNotFoundException();
        }
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);

        boolean self = target.userId().equals(userId);
        int currentDay = room.currentDayAt(LocalDateTime.now(clock), UploadSlot.dayStartHour());
        UserProfile profile = userProfileReader.findByUserId(target.userId()).orElse(null);

        return new MemberProfileResponse(
                target.id(),
                profile == null ? null : profile.nickname(),
                target.gender(),
                toFields(profile, target, currentDay, self),
                toValueAnswers(target.userId(), currentDay, self));
    }

    private List<ProfileFieldView> toFields(UserProfile profile, RoomMember target, int currentDay, boolean self) {
        List<ProfileFieldView> views = new ArrayList<>();
        for (ProfileField field : ProfileField.values()) {
            boolean visible = self || currentDay >= field.availableFromDay();
            views.add(new ProfileFieldView(field.key(), field.label(), field.availableFromDay(),
                    visible, visible ? field.extract(profile) : null));
        }
        boolean ageVisible = self || currentDay >= AGE_REVEAL_DAY;
        String age = ageVisible && target.birthday() != null
                ? String.valueOf(Period.between(target.birthday(), LocalDate.now(clock)).getYears())
                : null;
        views.add(new ProfileFieldView("age", "나이", AGE_REVEAL_DAY, ageVisible, age));
        return views;
    }

    private List<ValueAnswerView> toValueAnswers(Long targetUserId, int currentDay, boolean self) {
        boolean visible = self || currentDay >= VALUE_REVEAL_DAY;
        Map<Long, ValueQuestion> questions = valueQuestionReader.getAllActive().stream()
                .collect(Collectors.toMap(ValueQuestion::id, Function.identity()));

        return userValueAnswerReader.getAllByUserId(targetUserId).stream()
                .filter(answer -> questions.containsKey(answer.questionId()))
                .map(answer -> new ValueAnswerView(
                        answer.id(),
                        answer.questionId(),
                        questions.get(answer.questionId()).questionText(),
                        visible,
                        visible ? answer.answer() : null))
                .toList();
    }
}
