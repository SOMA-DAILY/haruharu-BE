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

    MemberProfileService service;

    // 2026-06-26 19:00 KST 고정 → 방날짜 2026-06-26. startedAt 앵커로 currentDay를 파생한다.
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZONE);

    @BeforeEach
    void setUp() {
        service = new MemberProfileService(roomReader, roomMemberReader, userProfileReader, userImageReader,
                valueQuestionReader, userValueAnswerReader, matchResultReader, s3StoragePort, clock);
    }

    private RoomMember member(Long id, Long roomId, Long userId) {
        return new RoomMember(id, roomId, userId, "FEMALE", LocalDate.of(2000, 1, 1), LocalDateTime.now());
    }

    // startedAt=방날짜(2026-06-26) 09:00에서 (currentDay-1)일 전 앵커 → currentDayAt(clock)=currentDay
    private Room room(Long roomId, int currentDay) {
        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 26, 9, 0).minusDays(currentDay - 1L);
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, startedAt, null);
    }

    private UserProfile profile(Long userId) {
        return new UserProfile(1L, userId,
                "서울", "카카오", 165, "INFP", "하루", DrinkingStatus.SOMETIMES, SmokingStatus.NONE);
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
    @DisplayName("Day1에는 닉네임·성별만 노출되고 지역·나이·직업 등은 가려진다(재화·해금 없음)")
    void givenDay1_whenGetProfile_thenOnlyNicknameAndGenderVisible() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        stubBasics(roomId, userId, meId, targetId, targetUserId, 1);

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.nickname()).isEqualTo("하루");
        assertThat(result.gender()).isEqualTo("FEMALE");

        assertThat(field(result, "location").visible()).isFalse();
        assertThat(field(result, "location").value()).isNull();
        assertThat(field(result, "age").visible()).isFalse();
        assertThat(field(result, "age").value()).isNull();
        ProfileFieldView height = field(result, "height");
        assertThat(height.visible()).isFalse();
        assertThat(height.value()).isNull();
        assertThat(height.availableFromDay()).isEqualTo(3);
    }

    @Test
    @DisplayName("Day3에는 지역·나이·직업·키·MBTI·음주·흡연이 전원에게 자동 공개된다")
    void givenDay3_whenGetProfile_thenAllFieldsVisible() {
        Long userId = 1L;
        Long targetUserId = 2L;
        Long roomId = 10L;
        Long meId = 20L;
        Long targetId = 30L;

        stubBasics(roomId, userId, meId, targetId, targetUserId, 3);

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(field(result, "location").value()).isEqualTo("서울");
        assertThat(field(result, "affiliation").value()).isEqualTo("카카오");
        assertThat(field(result, "height").value()).isEqualTo("165");
        assertThat(field(result, "mbti").value()).isEqualTo("INFP");
        assertThat(field(result, "drinking").value()).isEqualTo("가끔");
        assertThat(field(result, "smoking").value()).isEqualTo("안 피움");
        assertThat(field(result, "age").value()).isEqualTo("26");
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

        assertThat(field(result, "affiliation").value()).isEqualTo("카카오");
        assertThat(field(result, "age").value()).isEqualTo("26");
        assertThat(result.fields()).allMatch(ProfileFieldView::visible);
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
                new RoomMember(targetId, roomId, targetUserId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 3)));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.empty()); // 프로필 미작성
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(targetUserId)).willReturn(List.of());

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.nickname()).isNull();
        // Day3라 visible=true지만 프로필이 없어 값은 null
        assertThat(field(result, "affiliation").visible()).isTrue();
        assertThat(field(result, "affiliation").value()).isNull();
        assertThat(field(result, "age").visible()).isTrue();
        assertThat(field(result, "age").value()).isNull();
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
        assertThat(field(result, "location").value()).isEqualTo("서울");
        assertThat(field(result, "age").value()).isEqualTo("26");
        assertThat(result.valueAnswers()).hasSize(1);
        assertThat(result.valueAnswers().get(0).visible()).isTrue();
        assertThat(result.valueAnswers().get(0).answer()).isEqualTo("A");
        assertThat(result.imageRevealed()).isTrue();
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
