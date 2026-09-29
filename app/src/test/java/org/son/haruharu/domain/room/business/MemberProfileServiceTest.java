package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse.ProfileFieldView;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.son.haruharu.domain.value.implement.UserValueAnswer;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock org.son.haruharu.domain.room.implement.RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock UserProfileReader userProfileReader;
    @Mock UserImageReader userImageReader;
    @Mock ValueQuestionReader valueQuestionReader;
    @Mock UserValueAnswerReader userValueAnswerReader;
    @Mock MatchResultReader matchResultReader;
    @Mock S3StoragePort s3StoragePort;
    @Mock org.son.haruharu.domain.roomschedule.implement.RoomScheduleItemReader roomScheduleItemReader;

    MemberProfileService service;

    // 2026-06-26 19:00 KST 고정 → 방날짜 2026-06-26. startedAt 앵커로 currentDay를 파생한다.
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZONE);

    @BeforeEach
    void setUp() {
        service = new MemberProfileService(roomReader, roomMemberReader, userProfileReader, userImageReader,
                valueQuestionReader, userValueAnswerReader, matchResultReader, s3StoragePort, roomScheduleItemReader, clock);
    }

    private RoomMember member(Long id, Long roomId, Long userId) {
        return new RoomMember(id, roomId, userId, "FEMALE", "가명", LocalDate.of(2000, 1, 1), LocalDateTime.now());
    }

    // startedAt=방날짜(2026-06-26) 09:00에서 (currentDay-1)일 전 앵커 → currentDayAt(clock)=currentDay
    private Room room(Long roomId, int currentDay) {
        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 26, 9, 0).minusDays(currentDay - 1L);
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, startedAt, null);
    }

    private UserProfile profile(Long userId) {
        return new UserProfile(1L, userId,
                "서울", "카카오", 165, "INFP", "하루", DrinkingStatus.SOMETIMES, SmokingStatus.NONE, null)
                .withRoomProfile(null, "INFP", "전자공학과", "러닝");
    }

    private ProfileFieldView field(MemberProfileResponse res, String key) {
        return res.fields().stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow();
    }

    private void stubBasics(Long roomId, Long userId, Long meId, Long targetId, Long targetUserId, int currentDay) {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, targetUserId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, currentDay)));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(profile(targetUserId)));
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(targetUserId)).willReturn(List.of());
    }

    @Test
    @DisplayName("대표사진이 있으면 presigned GET URL로 변환해 반환한다")
    void givenRepresentativeImage_whenGetProfile_thenReturnsPresignedImageUrl() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;
        stubBasics(roomId, userId, meId, targetId, targetUserId, 1);
        String objectUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/users/2/images/rep.jpg";
        given(userImageReader.getRepresentativeImageUrl(targetUserId)).willReturn(objectUrl);
        given(s3StoragePort.createPresignedImageGetUrl(objectUrl)).willReturn(objectUrl + "?sig");

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.profileImageUrl()).isEqualTo(objectUrl + "?sig");
    }

    @Test
    @DisplayName("Day1에는 매칭 전이라 실명 대신 가명이 노출되고(nameRevealed=false) 성별만 함께 노출된다")
    void givenDay1_whenGetProfile_thenPseudonymAndGenderVisible() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        stubBasics(roomId, userId, meId, targetId, targetUserId, 1);

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        // 매칭 전이라 실명(하루)이 아니라 가명이 내려간다(#393)
        assertThat(result.nickname()).isEqualTo("가명");
        assertThat(result.nameRevealed()).isFalse();
        assertThat(result.gender()).isEqualTo("FEMALE");

        assertThat(field(result, "MBTI").visible()).isFalse();
        assertThat(field(result, "MBTI").value()).isNull();
        assertThat(field(result, "AGE").visible()).isFalse();
        assertThat(field(result, "AGE").value()).isNull();
        ProfileFieldView department = field(result, "DEPARTMENT");
        assertThat(department.visible()).isFalse();
        assertThat(department.value()).isNull();
        assertThat(department.availableFromDay()).isEqualTo(3);
        assertThat(department.revealAt()).isNull();
    }

    @Test
    @DisplayName("편성 없는 옛 방은 Day3에 MBTI·요즘 빠져 있는 것·나이·학과가 시트 순서대로 전원에게 공개된다")
    void givenDay3_whenGetProfile_thenAllFieldsVisible() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        stubBasics(roomId, userId, meId, targetId, targetUserId, 3);

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.fields()).extracting(ProfileFieldView::key)
                .containsExactly("MBTI", "INTO_NOW", "AGE", "DEPARTMENT");
        assertThat(field(result, "MBTI").value()).isEqualTo("INFP");
        assertThat(field(result, "INTO_NOW").value()).isEqualTo("러닝");
        assertThat(field(result, "DEPARTMENT").value()).isEqualTo("전자공학과");
        assertThat(field(result, "AGE").value()).isEqualTo("26");
        assertThat(result.fields()).allMatch(ProfileFieldView::visible);
    }

    @Test
    @DisplayName("본인 프로필은 Day와 무관하게 모든 항목이 공개된다")
    void givenSelf_whenGetProfile_thenAllVisibleEvenBeforeDay3() {
        Long userId = 1L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, userId)); // target==me
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 1)));
        given(userProfileReader.findByUserId(userId)).willReturn(Optional.of(profile(userId)));
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(userId)).willReturn(List.of());

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(field(result, "DEPARTMENT").value()).isEqualTo("전자공학과");
        assertThat(field(result, "AGE").value()).isEqualTo("26");
        assertThat(result.fields()).allMatch(ProfileFieldView::visible);
        // 본인은 항상 실명(가명 아님)·nameRevealed=true (#393)
        assertThat(result.nickname()).isEqualTo("하루");
        assertThat(result.nameRevealed()).isTrue();
        // 본인 프로필은 매칭 조회 없이 사진도 공개된다(단락평가로 isMatched 미호출)
        assertThat(result.imageRevealed()).isTrue();
        then(matchResultReader).should(never()).isMatched(any(), any());
    }

    @Test
    @DisplayName("프로필 미작성·birthday 없는 멤버는 Day3여도 예외 없이 닉네임·값·나이가 null로 내려간다")
    void givenNoProfileAndNoBirthday_whenGetProfile_thenNullSafe() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        // birthday null인 대상 멤버
        given(roomMemberReader.getById(targetId)).willReturn(
                new RoomMember(targetId, roomId, targetUserId, "MALE", null, null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 3)));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.empty()); // 프로필 미작성
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(targetUserId)).willReturn(List.of());

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        // 프로필·성별 모두 없어 가명도 없으니 실명 대신 익명 플레이스홀더가 내려간다(실명 폴백 금지 — #393)
        assertThat(result.nickname()).isEqualTo("익명");
        assertThat(result.nameRevealed()).isFalse();
        // Day3라 visible=true지만 프로필이 없어 값은 null
        assertThat(field(result, "DEPARTMENT").visible()).isTrue();
        assertThat(field(result, "DEPARTMENT").value()).isNull();
        assertThat(field(result, "AGE").visible()).isTrue();
        assertThat(field(result, "AGE").value()).isNull();
    }

    @Test
    @DisplayName("최종매칭 전에는 대표사진 presigned URL을 반환하되 imageRevealed=false다(클라 블러)")
    void givenNotMatched_whenGetProfile_thenImageUrlButNotRevealed() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        stubBasics(roomId, userId, meId, targetId, targetUserId, 2);
        given(userImageReader.getRepresentativeImageUrl(targetUserId)).willReturn("https://cdn/rep.jpg");
        given(s3StoragePort.createPresignedImageGetUrl("https://cdn/rep.jpg")).willReturn("https://cdn/rep.jpg?sig");
        given(matchResultReader.isMatched(meId, targetId)).willReturn(false);

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.profileImageUrl()).isEqualTo("https://cdn/rep.jpg?sig");
        assertThat(result.imageRevealed()).isFalse();
    }

    @Test
    @DisplayName("최종 상호매칭 성립 후에는 imageRevealed=true다")
    void givenMatched_whenGetProfile_thenImageRevealed() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        stubBasics(roomId, userId, meId, targetId, targetUserId, 3);
        given(userImageReader.getRepresentativeImageUrl(targetUserId)).willReturn("https://cdn/rep.jpg");
        given(s3StoragePort.createPresignedImageGetUrl("https://cdn/rep.jpg")).willReturn("https://cdn/rep.jpg?sig");
        given(matchResultReader.isMatched(meId, targetId)).willReturn(true);

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.imageRevealed()).isTrue();
        assertThat(result.profileImageUrl()).isEqualTo("https://cdn/rep.jpg?sig");
        // 상호매칭 후에는 가명이 아니라 실명(닉네임)이 공개된다(#393)
        assertThat(result.nickname()).isEqualTo("하루");
        assertThat(result.nameRevealed()).isTrue();
    }

    @Test
    @DisplayName("상호매칭 성립 후에는 룸이 만료(ENDED, currentDay=0)돼도 프로필 항목·가치관·나이가 계속 공개된다")
    void givenMatchedAndRoomEnded_whenGetProfile_thenStaysRevealed() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, targetUserId));
        // ENDED 방 → currentDayAt=0 (Day 기준이라면 전부 숨김 대상)
        given(roomReader.findById(roomId)).willReturn(Optional.of(
                new Room(roomId, RoomStatus.ENDED, LocalDate.of(2026, 6, 24), 3,
                        LocalDateTime.of(2026, 6, 24, 9, 0), LocalDateTime.of(2026, 6, 27, 9, 0))));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(profile(targetUserId)));
        given(matchResultReader.isMatched(meId, targetId)).willReturn(true);
        ValueQuestion question = new ValueQuestion(100L, "데이트는?", "A선택지", "B선택지", "DATING", 1, true);
        given(valueQuestionReader.getAllActive()).willReturn(List.of(question));
        given(userValueAnswerReader.getAllByUserId(targetUserId))
                .willReturn(List.of(new UserValueAnswer(500L, targetUserId, 100L, "A")));

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        // 매칭이면 currentDay=0이어도 전 항목 공개
        assertThat(result.fields()).allMatch(ProfileFieldView::visible);
        assertThat(field(result, "MBTI").value()).isEqualTo("INFP");
        assertThat(field(result, "AGE").value()).isEqualTo("26");
        assertThat(result.valueAnswers()).hasSize(1);
        assertThat(result.valueAnswers().get(0).visible()).isTrue();
        assertThat(result.valueAnswers().get(0).answer()).isEqualTo("A");
        assertThat(result.imageRevealed()).isTrue();
    }

    @Test
    @DisplayName("가치관 답변 A는 질문의 option_a 텍스트가 answer_text로 내려간다")
    void givenAnswerA_whenGetProfile_thenAnswerTextIsOptionA() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, targetUserId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 2))); // Day2 = 가치관 공개
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(profile(targetUserId)));
        given(valueQuestionReader.getAllActive()).willReturn(List.of(
                new ValueQuestion(100L, "연인과 주말을 보낸다면?", "함께 여행 가기", "집에서 쉬기", "DATING", 1, true)));
        given(userValueAnswerReader.getAllByUserId(targetUserId))
                .willReturn(List.of(new UserValueAnswer(500L, targetUserId, 100L, "A")));

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.valueAnswers()).hasSize(1);
        assertThat(result.valueAnswers().get(0).answer()).isEqualTo("A");
        assertThat(result.valueAnswers().get(0).answerText()).isEqualTo("함께 여행 가기");
    }

    @Test
    @DisplayName("가치관 답변 B는 질문의 option_b 텍스트가 answer_text로 내려간다")
    void givenAnswerB_whenGetProfile_thenAnswerTextIsOptionB() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, targetUserId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 2)));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(profile(targetUserId)));
        given(valueQuestionReader.getAllActive()).willReturn(List.of(
                new ValueQuestion(100L, "연인과 주말을 보낸다면?", "함께 여행 가기", "집에서 쉬기", "DATING", 1, true)));
        given(userValueAnswerReader.getAllByUserId(targetUserId))
                .willReturn(List.of(new UserValueAnswer(500L, targetUserId, 100L, "B")));

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.valueAnswers().get(0).answerText()).isEqualTo("집에서 쉬기");
    }

    @Test
    @DisplayName("Day1에는 가치관 답변이 비공개라 answer·answer_text 모두 null이다")
    void givenDay1_whenGetProfile_thenAnswerAndTextHidden() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, targetUserId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 1))); // Day1 = 가치관 비공개
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(profile(targetUserId)));
        given(valueQuestionReader.getAllActive()).willReturn(List.of(
                new ValueQuestion(100L, "연인과 주말을 보낸다면?", "함께 여행 가기", "집에서 쉬기", "DATING", 1, true)));
        given(userValueAnswerReader.getAllByUserId(targetUserId))
                .willReturn(List.of(new UserValueAnswer(500L, targetUserId, 100L, "A")));

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.valueAnswers().get(0).visible()).isFalse();
        assertThat(result.valueAnswers().get(0).answer()).isNull();
        assertThat(result.valueAnswers().get(0).answerText()).isNull();
    }

    // ── #457 F2: 편성 기준 공개 ──

    private void stubScheduledRoom(Long roomId, Long userId, Long meId, Long targetId, Long targetUserId) {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, targetUserId));
        // 방 시작 2026-06-26 09:00, 지금(clock) 19:00 = DAY1
        LocalDateTime start = LocalDateTime.of(2026, 6, 26, 9, 0);
        Room scheduled = Room.createScheduled(start, start.plusHours(56), start.plusHours(59), start.plusHours(60),
                start.plusHours(72), 3);
        given(roomReader.findById(roomId)).willReturn(Optional.of(new Room(roomId, scheduled.status(),
                scheduled.scheduledDate(), 3, null, null, scheduled.startsAt(), scheduled.finalSelectionOpensAt(),
                scheduled.finalSelectionClosesAt(), scheduled.resultAt(), scheduled.endsAt())));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(profile(targetUserId)));
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(targetUserId)).willReturn(List.of());
        given(roomScheduleItemReader.hasRevealSchedule(roomId)).willReturn(true);
    }

    @Test
    @DisplayName("AGE 행만 있던 방에서 그 행을 취소하면 옛 DAY3 규칙으로 돌아가지 않고 DAY3 에도 AGE 는 안 열린다")
    void givenOnlyAgeRowCanceled_whenDay3_thenAgeStaysHidden() {
        stubBasics(10L, 1L, 20L, 30L, 2L, 3);
        given(roomScheduleItemReader.hasRevealSchedule(10L)).willReturn(true); // 취소 행 포함
        given(roomScheduleItemReader.findRevealTimes(10L)).willReturn(java.util.Map.of()); // 취소 제외하면 없음

        MemberProfileResponse result = service.getMemberProfile(10L, 30L, 1L);

        assertThat(field(result, "AGE").visible()).isFalse();
        assertThat(field(result, "AGE").value()).isNull();
        assertThat(result.fields()).noneMatch(ProfileFieldView::visible);
    }

    @Test
    @DisplayName("편성 공개 시각이 지난 항목만 값이 보이고, 안 지난 항목은 value=null + reveal_at + DAY(달력 기준)")
    void givenScheduledReveals_whenGetProfile_thenOnlyPastRevealsVisible() {
        stubScheduledRoom(10L, 1L, 20L, 30L, 2L);
        given(roomScheduleItemReader.findRevealTimes(10L)).willReturn(java.util.Map.of(
                org.son.haruharu.domain.roomschedule.implement.RevealField.MBTI, LocalDateTime.of(2026, 6, 26, 18, 0),
                org.son.haruharu.domain.roomschedule.implement.RevealField.INTO_NOW, LocalDateTime.of(2026, 6, 26, 18, 0),
                org.son.haruharu.domain.roomschedule.implement.RevealField.AGE, LocalDateTime.of(2026, 6, 27, 12, 0)));

        MemberProfileResponse result = service.getMemberProfile(10L, 30L, 1L);

        assertThat(field(result, "MBTI").value()).isEqualTo("INFP");
        assertThat(field(result, "INTO_NOW").visible()).isTrue();
        ProfileFieldView age = field(result, "AGE");
        assertThat(age.visible()).isFalse();
        assertThat(age.value()).isNull();
        assertThat(age.revealAt()).isEqualTo(LocalDateTime.of(2026, 6, 27, 12, 0));
        assertThat(age.availableFromDay()).isEqualTo(2);
        // 편성에 없는(취소된) 항목은 끝까지 안 열린다
        ProfileFieldView department = field(result, "DEPARTMENT");
        assertThat(department.visible()).isFalse();
        assertThat(department.revealAt()).isNull();
    }

    @Test
    @DisplayName("편성이 있어도 매칭 상대는 시각과 상관없이 전부 공개, 나이는 올해 − birth_year")
    void givenScheduledRoomAndMatched_whenGetProfile_thenAllVisible() {
        stubScheduledRoom(10L, 1L, 20L, 30L, 2L);
        given(userProfileReader.findByUserId(2L)).willReturn(Optional.of(profile(2L).withRoomProfile(2003, "INFP", "전자공학과", "러닝")));
        given(roomScheduleItemReader.findRevealTimes(10L)).willReturn(java.util.Map.of(
                org.son.haruharu.domain.roomschedule.implement.RevealField.AGE, LocalDateTime.of(2026, 6, 27, 12, 0)));
        given(matchResultReader.isMatched(20L, 30L)).willReturn(true);

        MemberProfileResponse result = service.getMemberProfile(10L, 30L, 1L);

        assertThat(result.fields()).allMatch(ProfileFieldView::visible);
        assertThat(field(result, "AGE").value()).isEqualTo("23");
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetProfile_thenThrows() {
        Long userId = 1L;
        Long roomId = 10L;
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getMemberProfile(roomId, 30L, userId))
                .isInstanceOf(NotRoomMemberException.class);
    }

    @Test
    @DisplayName("다른 방 멤버의 프로필을 조회하면 RoomMemberNotFoundException이 발생한다")
    void givenMemberInAnotherRoom_whenGetProfile_thenThrows() {
        Long userId = 1L;
        Long roomId = 10L;
        Long otherRoomId = 99L;
        Long meId = 20L;
        Long targetId = 30L;

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, otherRoomId, 2L));

        assertThatThrownBy(() -> service.getMemberProfile(roomId, targetId, userId))
                .isInstanceOf(RoomMemberNotFoundException.class);
    }
}
