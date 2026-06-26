package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MembersResponse;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
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

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock UserProfileReader userProfileReader;

    private RoomMembersService service() {
        return new RoomMembersService(roomReader, roomMemberReader, userProfileReader, clock);
    }

    private UserProfile profile(UUID userId) {
        return new UserProfile(UUID.randomUUID(), userId, "MALE", LocalDate.of(2000, 1, 1),
                "서울", "ABC대학교", 180, "INTJ", "철수", "SOMETIMES", "NONE", "NONE");
    }

    @Test
    @DisplayName("Day3 이전에는 직업/키/MBTI가 가려지고 닉네임·지역·나이·내 열쇠 잔액은 노출된다")
    void givenPreDay3_whenGetMembers_thenDetailGated() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomMember me = new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", 15, LocalDateTime.now());
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, 1, LocalDateTime.now(), null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me);
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(me));
        given(userProfileReader.findByUserId(userId)).willReturn(Optional.of(profile(userId)));

        MembersResponse result = service().getMembers(roomId, userId);

        assertThat(result.myPointSum()).isEqualTo(15);
        MembersResponse.MemberView view = result.members().get(0);
        assertThat(view.me()).isTrue();
        assertThat(view.nickname()).isEqualTo("철수");
        assertThat(view.location()).isEqualTo("서울");
        assertThat(view.age()).isEqualTo(26);
        assertThat(view.affiliation()).isNull();
        assertThat(view.height()).isNull();
        assertThat(view.mbti()).isNull();
    }

    @Test
    @DisplayName("Day3에는 직업/키/MBTI가 공개된다")
    void givenDay3_whenGetMembers_thenDetailRevealed() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomMember me = new RoomMember(UUID.randomUUID(), roomId, userId, "MALE", 20, LocalDateTime.now());
        Room room = new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, 3, LocalDateTime.now(), null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me);
        given(roomReader.findById(roomId)).willReturn(Optional.of(room));
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(me));
        given(userProfileReader.findByUserId(userId)).willReturn(Optional.of(profile(userId)));

        MembersResponse result = service().getMembers(roomId, userId);

        MembersResponse.MemberView view = result.members().get(0);
        assertThat(view.affiliation()).isEqualTo("ABC대학교");
        assertThat(view.height()).isEqualTo(180);
        assertThat(view.mbti()).isEqualTo("INTJ");
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
