package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MembersResponse;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoomMembersServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-06-26 고정 → 2000-01-01생은 26세
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 26, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @Mock RoomMemberReader roomMemberReader;
    @Mock UserProfileReader userProfileReader;

    private RoomMembersService service() {
        return new RoomMembersService(roomMemberReader, userProfileReader, clock);
    }

    private UserProfile profile(UUID userId) {
        return new UserProfile(UUID.randomUUID(), userId, "MALE", LocalDate.of(2000, 1, 1),
                "서울", "ABC대학교", 180, "INTJ", "철수", "SOMETIMES", "NONE", "NONE");
    }

    @Test
    @DisplayName("목록은 닉네임·성별·지역·나이·내 열쇠 잔액만 담고 해금 대상 항목은 포함하지 않는다")
    void givenMember_whenGetMembers_thenBasicsOnly() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomMember me = new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", 15, LocalDateTime.now());

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me);
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(me));
        given(userProfileReader.findByUserId(userId)).willReturn(Optional.of(profile(userId)));

        MembersResponse result = service().getMembers(roomId, userId);

        assertThat(result.myPointSum()).isEqualTo(15);
        MembersResponse.MemberView view = result.members().get(0);
        assertThat(view.me()).isTrue();
        assertThat(view.nickname()).isEqualTo("철수");
        assertThat(view.location()).isEqualTo("서울");
        assertThat(view.age()).isEqualTo(26);
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetMembers_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service().getMembers(roomId, userId))
            .isInstanceOf(NotRoomMemberException.class);
    }
}
