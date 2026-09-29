package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import org.son.haruharu.domain.roomschedule.implement.RevealField;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItemReader;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방 멤버 단건 프로필 조회. 항목별 공개(#457, F2)는 방 편성의 공개 시각(취소 제외 행 중 가장 이른 run_at)이 지나면 열리고,
 * 편성에 공개 행이 없는 옛 방은 currentDay >= 3 규칙. 공개 전 value 는 서버가 null 로 내린다. 재화·해금 호출이 없다.
 * room·profile·value 세 도메인의 Reader를 조율한다(같은 방 멤버 한정).
 */
@Service
@Transactional(readOnly = true)
public class MemberProfileService {

    /** 나이 공개 Day. 나이는 UserProfile이 아닌 본인인증 스냅샷(RoomMember.birthday)에서 파생한다. 목록(RoomMembersService)도 같은 기준을 참조한다(#214). */
    static final int AGE_REVEAL_DAY = 3;
    /** 방 멤버 목록의 지역 공개 Day(#214). 지역은 #457 에서 단건 프로필 항목에서 빠졌고 목록만 이 기준을 쓴다. */
    static final int LOCATION_REVEAL_DAY = 3;
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
    private final RoomScheduleItemReader roomScheduleItemReader;
    private final Clock clock;

    public MemberProfileService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                UserProfileReader userProfileReader, UserImageReader userImageReader,
                                ValueQuestionReader valueQuestionReader,
                                UserValueAnswerReader userValueAnswerReader,
                                MatchResultReader matchResultReader, S3StoragePort s3StoragePort,
                                RoomScheduleItemReader roomScheduleItemReader, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.userProfileReader = userProfileReader;
        this.userImageReader = userImageReader;
        this.valueQuestionReader = valueQuestionReader;
        this.userValueAnswerReader = userValueAnswerReader;
        this.matchResultReader = matchResultReader;
        this.s3StoragePort = s3StoragePort;
        this.roomScheduleItemReader = roomScheduleItemReader;
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
        LocalDateTime now = LocalDateTime.now(clock);
        int currentDay = room.currentDayAt(now);
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
                toFields(room, profile, target, now, currentDay, fullyRevealed),
                toValueAnswers(target.userId(), currentDay, fullyRevealed));
    }

    private List<ProfileFieldView> toFields(Room room, UserProfile profile, RoomMember target, LocalDateTime now,
                                            int currentDay, boolean fullyRevealed) {
        Map<RevealField, LocalDateTime> revealTimes = roomScheduleItemReader.findRevealTimes(room.id());
        boolean scheduled = !revealTimes.isEmpty();
        List<ProfileFieldView> views = new ArrayList<>();
        for (ProfileField field : ProfileField.values()) {
            LocalDateTime revealAt = scheduled ? revealTimes.get(RevealField.valueOf(field.name())) : null;
            boolean revealed = scheduled
                    ? revealAt != null && !revealAt.isAfter(now) // 취소만 된 항목은 끝까지 안 열린다
                    : currentDay >= field.availableFromDay();   // 편성 없는 옛 방: DAY3
            boolean visible = fullyRevealed || revealed;
            views.add(new ProfileFieldView(field.key(), field.label(), availableFromDay(field, revealAt, room),
                    visible, visible ? valueOf(field, profile, target, now) : null, revealAt));
        }
        return views;
    }

    /** reveal_at 의 DAY(달력 날짜 차 + 1). 편성 시각이 없으면 옛 기본값(3). 지금 앱이 숫자로 파싱해서 계속 채운다. */
    private static int availableFromDay(ProfileField field, LocalDateTime revealAt, Room room) {
        LocalDateTime anchor = room.startsAt() != null ? room.startsAt() : room.startedAt();
        if (revealAt == null || anchor == null) {
            return field.availableFromDay();
        }
        return (int) ChronoUnit.DAYS.between(anchor.toLocalDate(), revealAt.toLocalDate()) + 1;
    }

    /** 나이 = 올해 − 출생연도(만 나이 아님). birth_year 가 없는 기존 참가자는 본인인증 생일(RoomMember.birthday)의 연도로. */
    private static String valueOf(ProfileField field, UserProfile profile, RoomMember target, LocalDateTime now) {
        if (field != ProfileField.AGE) {
            return field.extract(profile);
        }
        Integer birthYear = profile != null && profile.birthYear() != null ? profile.birthYear()
                : target.birthday() != null ? target.birthday().getYear() : null;
        return birthYear == null ? null : String.valueOf(now.getYear() - birthYear);
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
