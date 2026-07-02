package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.profile.implement.ProfileField;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalog;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalogReader;
import org.son.haruharu.domain.profile.implement.ProfileFieldUnlockReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse.UnlockableFieldView;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 방 멤버 단건 프로필 조회. 해금 항목 정책은 카탈로그(데이터)에서 읽어 항목별 상태를 계산한다.
 * room·profile·value 세 도메인의 Reader를 조율한다(같은 방 멤버 한정).
 */
@Service
@Transactional(readOnly = true)
public class MemberProfileService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final UserProfileReader userProfileReader;
    private final ValueQuestionReader valueQuestionReader;
    private final UserValueAnswerReader userValueAnswerReader;
    private final ValueAnswerUnlockReader valueAnswerUnlockReader;
    private final ProfileFieldCatalogReader profileFieldCatalogReader;
    private final ProfileFieldUnlockReader profileFieldUnlockReader;
    private final Clock clock;

    public MemberProfileService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                UserProfileReader userProfileReader, ValueQuestionReader valueQuestionReader,
                                UserValueAnswerReader userValueAnswerReader,
                                ValueAnswerUnlockReader valueAnswerUnlockReader,
                                ProfileFieldCatalogReader profileFieldCatalogReader,
                                ProfileFieldUnlockReader profileFieldUnlockReader, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.userProfileReader = userProfileReader;
        this.valueQuestionReader = valueQuestionReader;
        this.userValueAnswerReader = userValueAnswerReader;
        this.valueAnswerUnlockReader = valueAnswerUnlockReader;
        this.profileFieldCatalogReader = profileFieldCatalogReader;
        this.profileFieldUnlockReader = profileFieldUnlockReader;
        this.clock = clock;
    }

    public MemberProfileResponse getMemberProfile(UUID roomId, UUID memberId, UUID userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId);
        RoomMember target = roomMemberReader.getById(memberId);
        if (!target.roomId().equals(roomId)) {
            throw new RoomMemberNotFoundException();
        }
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);

        boolean self = target.userId().equals(userId);
        UserProfile profile = userProfileReader.findByUserId(target.userId()).orElse(null);
        Integer age = target.birthday() == null
                ? null : Period.between(target.birthday(), LocalDate.now(clock)).getYears();

        List<UnlockableFieldView> unlockableFields =
                toUnlockableFields(profile, room.currentDay(), me.id(), target.id(), self);
        List<ValueAnswerView> valueAnswers = toValueAnswers(target.userId(), me.id(), self);

        return new MemberProfileResponse(
                target.id(),
                profile == null ? null : profile.nickname(),
                target.gender(),
                profile == null ? null : profile.location(),
                age,
                unlockableFields,
                valueAnswers);
    }

    private List<UnlockableFieldView> toUnlockableFields(UserProfile profile, int currentDay,
                                                         UUID viewerRoomMemberId, UUID targetRoomMemberId,
                                                         boolean self) {
        Set<String> unlockedKeys = self
                ? Set.of()
                : Set.copyOf(profileFieldUnlockReader.getUnlockedFieldKeys(viewerRoomMemberId, targetRoomMemberId));

        List<UnlockableFieldView> views = new ArrayList<>();
        for (ProfileFieldCatalog catalog : profileFieldCatalogReader.getAllActive()) {
            ProfileField field = ProfileField.findByKey(catalog.fieldKey()).orElse(null);
            if (field == null) {
                continue; // 카탈로그에 있으나 대응 enum이 없는 항목(제거됨)은 조용히 skip
            }
            boolean unlocked = self || unlockedKeys.contains(catalog.fieldKey());
            views.add(new UnlockableFieldView(
                    catalog.fieldKey(),
                    field.label(),
                    catalog.unlockCost(),
                    catalog.availableFromDay(),
                    catalog.isUnlockableOn(currentDay),
                    unlocked,
                    unlocked ? field.extract(profile) : null));
        }
        return views;
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
