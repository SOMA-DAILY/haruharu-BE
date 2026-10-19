package org.son.haruharu.domain.profile.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.profile.business.command.UpdateRoomProfileCommand;
import org.son.haruharu.domain.profile.business.dto.RoomProfileResponse;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.son.haruharu.domain.profile.implement.exception.InvalidRoomProfileException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RoomProfileServiceTest {

    private static final Long USER = 7L;

    @Mock UserProfileReader userProfileReader;
    @Mock UserProfileWriter userProfileWriter;

    RoomProfileService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-29T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new RoomProfileService(userProfileReader, userProfileWriter, clock);
    }

    private UserProfile existing() {
        return new UserProfile(1L, USER, "서울", "직장인", 175, "INFP", "테스터",
                DrinkingStatus.SOMETIMES, SmokingStatus.NONE, Gender.FEMALE);
    }

    @Test
    @DisplayName("저장은 4칸만 바꾸고 닉네임, 지역, 하는 일, 키, 성별은 그대로 둔다")
    void givenExistingProfile_whenUpdateRoomProfile_thenChangesOnlyFourFields() {
        given(userProfileReader.findByUserId(USER)).willReturn(Optional.of(existing()));
        given(userProfileWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        RoomProfileResponse result = service.updateRoomProfile(USER,
                new UpdateRoomProfileCommand(2003, "enfp", "전자공학과", "러닝"));

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        then(userProfileWriter).should().save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(new UserProfile(1L, USER, "서울", "직장인", 175, "ENFP", "테스터",
                DrinkingStatus.SOMETIMES, SmokingStatus.NONE, Gender.FEMALE, 2003, "전자공학과", "러닝", null));
        assertThat(result).isEqualTo(new RoomProfileResponse(2003, 23, "ENFP", "전자공학과", "러닝", true));
    }

    @Test
    @DisplayName("검증에 실패하면 저장하지 않는다")
    void givenInvalidMbti_whenUpdateRoomProfile_thenThrowsWithoutSaving() {
        assertThatThrownBy(() -> service.updateRoomProfile(USER,
                new UpdateRoomProfileCommand(2003, "ENFX", "전자공학과", "러닝")))
                .isInstanceOf(InvalidRoomProfileException.class);
        then(userProfileWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("프로필 행이 없으면 빈 값과 completed=false 로 답한다")
    void givenNoProfile_whenGetRoomProfile_thenEmptyAndIncomplete() {
        given(userProfileReader.findByUserId(USER)).willReturn(Optional.empty());

        assertThat(service.getRoomProfile(USER))
                .isEqualTo(new RoomProfileResponse(null, null, null, null, null, false));
    }

    @Test
    @DisplayName("기존 프로필 PUT(applyTo)은 방 온보딩 3칸을 지우지 않는다")
    void givenRoomProfile_whenLegacyProfileUpdate_thenKeepsRoomFields() {
        UserProfile withRoom = existing().withRoomProfile(2003, "ENFP", "전자공학과", "러닝");

        UserProfile updated = new UpdateProfileCommand("부산", "학생", 170, "ISTJ", "새이름", null, null, null)
                .applyTo(withRoom);

        assertThat(updated.birthYear()).isEqualTo(2003);
        assertThat(updated.department()).isEqualTo("전자공학과");
        assertThat(updated.intoNow()).isEqualTo("러닝");
        assertThat(updated.mbti()).isEqualTo("ISTJ");
    }
}
