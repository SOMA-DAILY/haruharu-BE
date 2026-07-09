package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MatchResultResponse;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.storage.exception.MatchResultNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MatchResultServiceTest {

    @Mock RoomMemberReader roomMemberReader;
    @Mock MatchResultReader matchResultReader;
    @Mock ChatRoomReader chatRoomReader;
    @Mock UserProfileReader userProfileReader;
    @Mock UserImageReader userImageReader;

    MatchResultService service;

    private final Long roomId = 10L;
    private final Long userId = 1L;
    private final Long meId = 20L;
    private final Long partnerId = 30L;
    private final Long partnerUserId = 2L;
    private final Long matchResultId = 40L;

    @BeforeEach
    void setUp() {
        service = new MatchResultService(roomMemberReader, matchResultReader, chatRoomReader,
                userProfileReader, userImageReader);
    }

    private RoomMember me() {
        return new RoomMember(meId, roomId, userId, "MALE", null, LocalDateTime.now());
    }

    private RoomMember partner() {
        return new RoomMember(partnerId, roomId, partnerUserId, "FEMALE", null, LocalDateTime.now());
    }

    private UserProfile profileOf(String nickname) {
        return new UserProfile(5L, partnerUserId, "서울", "직장인", 168, "INFP", nickname, null, null);
    }

    @Test
    @DisplayName("매칭이 있으면 상대 멤버 id와 함께 chatRoomId·상대 프로필 요약(닉네임·대표사진)을 반환한다")
    void givenMatched_whenGet_thenReturnsPartnerWithChatRoomAndProfile() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me());
        // me가 memberB인 케이스
        given(matchResultReader.getByMember(meId))
                .willReturn(new MatchResult(matchResultId, partnerId, meId, LocalDateTime.now()));
        given(roomMemberReader.getById(partnerId)).willReturn(partner());
        given(chatRoomReader.findChatRoomIdByMatchResultId(matchResultId)).willReturn(Optional.of(77L));
        given(userProfileReader.findByUserId(partnerUserId)).willReturn(Optional.of(profileOf("하늘")));
        given(userImageReader.getRepresentativeImageUrl(partnerUserId)).willReturn("https://cdn/rep.jpg");

        MatchResultResponse result = service.getMatchResult(roomId, userId);

        assertThat(result.partnerMemberId()).isEqualTo(partnerId);
        assertThat(result.chatRoomId()).isEqualTo(77L);
        assertThat(result.partner().nickname()).isEqualTo("하늘");
        assertThat(result.partner().profileImageUrl()).isEqualTo("https://cdn/rep.jpg");
    }

    @Test
    @DisplayName("상대 대표사진·채팅방이 아직 없으면 해당 필드는 null로 반환한다")
    void givenNoImageOrChatRoom_whenGet_thenNullFields() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me());
        given(matchResultReader.getByMember(meId))
                .willReturn(new MatchResult(matchResultId, meId, partnerId, LocalDateTime.now()));
        given(roomMemberReader.getById(partnerId)).willReturn(partner());
        given(chatRoomReader.findChatRoomIdByMatchResultId(matchResultId)).willReturn(Optional.empty());
        given(userProfileReader.findByUserId(partnerUserId)).willReturn(Optional.of(profileOf("하늘")));
        given(userImageReader.getRepresentativeImageUrl(partnerUserId)).willReturn(null);

        MatchResultResponse result = service.getMatchResult(roomId, userId);

        assertThat(result.chatRoomId()).isNull();
        assertThat(result.partner().profileImageUrl()).isNull();
        assertThat(result.partner().nickname()).isEqualTo("하늘");
    }

    @Test
    @DisplayName("매칭이 없으면 MatchResultNotFoundException(404)이 발생한다")
    void givenNoMatch_whenGet_thenThrows() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me());
        given(matchResultReader.getByMember(meId)).willThrow(new MatchResultNotFoundException());

        assertThatThrownBy(() -> service.getMatchResult(roomId, userId))
                .isInstanceOf(MatchResultNotFoundException.class);
    }
}
