package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.son.haruharu.domain.value.implement.UserValueAnswer;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.ValueAnswerUnlockReader;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock org.son.haruharu.domain.room.implement.RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock UserProfileReader userProfileReader;
    @Mock ValueQuestionReader valueQuestionReader;
    @Mock UserValueAnswerReader userValueAnswerReader;
    @Mock ValueAnswerUnlockReader valueAnswerUnlockReader;

    MemberProfileService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZONE);

    @BeforeEach
    void setUp() {
        service = new MemberProfileService(roomReader, roomMemberReader, userProfileReader,
                valueQuestionReader, userValueAnswerReader, valueAnswerUnlockReader, clock);
    }

    private RoomMember member(UUID id, UUID roomId, UUID userId) {
        return new RoomMember(id, roomId, userId, "FEMALE", 30, LocalDateTime.now());
    }

    private Room room(UUID roomId, int currentDay) {
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, currentDay,
                LocalDateTime.now(), null);
    }

    private UserProfile profile(UUID userId) {
        return new UserProfile(UUID.randomUUID(), userId, "FEMALE", LocalDate.of(2000, 1, 1),
                "서울", "카카오", 165, "INFP", "하루", "SOMETIMES", "NONE", "NONE");
    }

    @Test
    @DisplayName("Day3 미만이면 직업/키/MBTI는 가려지고 닉네임·지역·나이는 노출된다")
    void givenBeforeDay3_whenGetProfile_thenBasicFieldsHidden() {
        UUID userId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID meId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, targetUserId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 1)));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(profile(targetUserId)));
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(targetUserId)).willReturn(List.of());
        given(valueAnswerUnlockReader.getUnlockedAnswerIds(meId)).willReturn(List.of());

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.nickname()).isEqualTo("하루");
        assertThat(result.location()).isEqualTo("서울");
        assertThat(result.age()).isEqualTo(26);
        assertThat(result.affiliation()).isNull();
        assertThat(result.height()).isNull();
        assertThat(result.mbti()).isNull();
    }

    @Test
    @DisplayName("Day3 이상이면 직업/키/MBTI까지 공개되고, 해금한 가치관 답변만 내용이 보인다")
    void givenDay3AndUnlock_whenGetProfile_thenRevealedAndUnlockedAnswerShown() {
        UUID userId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID meId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, targetUserId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 3)));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(profile(targetUserId)));
        given(valueQuestionReader.getAllActive()).willReturn(List.of(
                new ValueQuestion(q1, "주말엔?", "LIFE", 1, true, 10),
                new ValueQuestion(q2, "이상형은?", "LOVE", 2, true, 20)));
        given(userValueAnswerReader.getAllByUserId(targetUserId)).willReturn(List.of(
                new UserValueAnswer(a1, targetUserId, q1, "집에서 휴식"),
                new UserValueAnswer(a2, targetUserId, q2, "다정한 사람")));
        given(valueAnswerUnlockReader.getUnlockedAnswerIds(meId)).willReturn(List.of(a1));

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.affiliation()).isEqualTo("카카오");
        assertThat(result.height()).isEqualTo(165);
        assertThat(result.mbti()).isEqualTo("INFP");
        assertThat(result.valueAnswers()).hasSize(2);

        var unlocked = result.valueAnswers().stream().filter(v -> v.answerId().equals(a1)).findFirst().orElseThrow();
        assertThat(unlocked.unlocked()).isTrue();
        assertThat(unlocked.answer()).isEqualTo("집에서 휴식");

        var locked = result.valueAnswers().stream().filter(v -> v.answerId().equals(a2)).findFirst().orElseThrow();
        assertThat(locked.unlocked()).isFalse();
        assertThat(locked.answer()).isNull();
        assertThat(locked.unlockCost()).isEqualTo(20);
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetProfile_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getMemberProfile(roomId, UUID.randomUUID(), userId))
                .isInstanceOf(NotRoomMemberException.class);
    }

    @Test
    @DisplayName("다른 방 멤버의 프로필을 조회하면 RoomMemberNotFoundException이 발생한다")
    void givenMemberInAnotherRoom_whenGetProfile_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID otherRoomId = UUID.randomUUID();
        UUID meId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, otherRoomId, UUID.randomUUID()));

        assertThatThrownBy(() -> service.getMemberProfile(roomId, targetId, userId))
                .isInstanceOf(RoomMemberNotFoundException.class);
    }
}
