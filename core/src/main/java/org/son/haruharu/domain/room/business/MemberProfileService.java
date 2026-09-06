package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.son.haruharu.domain.profile.implement.ProfileField;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse.ProfileFieldView;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse.ValueAnswerView;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방 멤버 단건 프로필 조회. 항목별 공개는 currentDay 기반 자동 공개(ProfileField의 공개Day). 재화·해금 호출이 없다.
 * room·profile·value 세 도메인의 Reader를 조율한다(같은 방 멤버 한정).
 */
@Service
@Transactional(readOnly = true)
public class MemberProfileService {

    /** 나이 공개 Day. 나이는 UserProfile이 아닌 본인인증 스냅샷(RoomMember.birthday)에서 파생한다. 목록(RoomMembersService)도 같은 기준을 참조한다(#214). */
    static final int AGE_REVEAL_DAY = 3;
    /** 가치관 답변 공개 Day. */
    private static final int VALUE_REVEAL_DAY = 2;

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final UserProfileReader userProfileReader;
    private final UserImageReader userImageReader;
    private final ValueQuestionReader valueQuestionReader;
    private final UserValueAnswerReader userValueAnswerReader;
    private final MatchResultReader matchResultReader;
    private final S3StoragePort s3StoragePort;
    private final Clock clock;

    public MemberProfileService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                UserProfileReader userProfileReader, UserImageReader userImageReader,
                                ValueQuestionReader valueQuestionReader,
                                UserValueAnswerReader userValueAnswerReader,
                                MatchResultReader matchResultReader, S3StoragePort s3StoragePort,
                                Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.userProfileReader = userProfileReader;
        this.userImageReader = userImageReader;
        this.valueQuestionReader = valueQuestionReader;
        this.userValueAnswerReader = userValueAnswerReader;
        this.matchResultReader = matchResultReader;
        this.s3StoragePort = s3StoragePort;
        this.clock = clock;
    }

    public MemberProfileResponse getMemberProfile(Long roomId, Long memberId, Long userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        RoomMember target = roomMemberReader.getById(memberId);
        if (!target.roomId().equals(roomId)) {
            throw new RoomMemberNotFoundException();
        }
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);

        boolean self = target.userId().equals(userId);
        int currentDay = room.currentDayAt(LocalDateTime.now(clock));
        UserProfile profile = userProfileReader.findByUserId(target.userId()).orElse(null);

        // 상호매칭은 영구 사실이라 룸 만료(currentDay=0) 이후에도 항목 공개가 유지된다. self는 단락평가로 isMatched 미호출.
        boolean matched = !self && matchResultReader.isMatched(me.id(), target.id());
        // 본인이거나 매칭 성립 시 모든 항목을 Day와 무관하게 공개한다(이미지 공개 기준과 동일).
        boolean fullyRevealed = self || matched;

        // 대표사진은 presigned GET URL로 항상 반환하고, 최종 상호매칭(또는 본인) 전까지는 imageRevealed=false로 두어
        // 클라이언트가 블러 처리하게 한다.
        String profileImageUrl = s3StoragePort.createPresignedImageGetUrl(
                userImageReader.getRepresentativeImageUrl(target.userId()));

        // 이름 공개(#393): 본인·상호매칭이면 실명(닉네임), 그 전에는 방 세션 가명(없으면 익명). imageRevealed와 동일 게이트(fullyRevealed).
        String displayName = fullyRevealed
                ? (profile == null ? null : profile.nickname())
                : target.displayPseudonym();

        return new MemberProfileResponse(
                target.id(),
                displayName,
                fullyRevealed,
                target.gender(),
                profileImageUrl,
                fullyRevealed,
                toFields(profile, target, currentDay, fullyRevealed),
                toValueAnswers(target.userId(), currentDay, fullyRevealed));
    }

    private List<ProfileFieldView> toFields(UserProfile profile, RoomMember target, int currentDay, boolean fullyRevealed) {
        List<ProfileFieldView> views = new ArrayList<>();
        for (ProfileField field : ProfileField.values()) {
            boolean visible = fullyRevealed || currentDay >= field.availableFromDay();
            views.add(new ProfileFieldView(field.key(), field.label(), field.availableFromDay(),
                    visible, visible ? field.extract(profile) : null));
        }
        boolean ageVisible = fullyRevealed || currentDay >= AGE_REVEAL_DAY;
        String age = ageVisible && target.birthday() != null
                ? String.valueOf(Period.between(target.birthday(), LocalDate.now(clock)).getYears())
                : null;
        views.add(new ProfileFieldView("age", "나이", AGE_REVEAL_DAY, ageVisible, age));
        return views;
    }

    private List<ValueAnswerView> toValueAnswers(Long targetUserId, int currentDay, boolean fullyRevealed) {
        boolean visible = fullyRevealed || currentDay >= VALUE_REVEAL_DAY;
        Map<Long, ValueQuestion> questions = valueQuestionReader.getAllActive().stream()
                .collect(Collectors.toMap(ValueQuestion::id, Function.identity()));

        return userValueAnswerReader.getAllByUserId(targetUserId).stream()
                .filter(answer -> questions.containsKey(answer.questionId()))
                .map(answer -> {
                    ValueQuestion question = questions.get(answer.questionId());
                    return new ValueAnswerView(
                            answer.id(),
                            answer.questionId(),
                            question.questionText(),
                            visible,
                            visible ? answer.answer() : null,
                            visible ? answerText(question, answer.answer()) : null);
                })
                .toList();
    }

    /** 저장값 A/B를 질문의 선택지 텍스트로 변환한다(응답 시점 조인 — 저장 스냅샷 아님). A/B 외 값은 null. */
    private String answerText(ValueQuestion question, String answer) {
        if ("A".equals(answer)) {
            return question.optionA();
        }
        if ("B".equals(answer)) {
            return question.optionB();
        }
        return null;
    }
}
