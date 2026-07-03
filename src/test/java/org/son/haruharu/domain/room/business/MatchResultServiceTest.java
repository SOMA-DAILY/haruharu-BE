package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    MatchResultService service;

    private final Long roomId = 10L;
    private final Long userId = 1L;
    private final Long meId = 20L;
    private final Long partnerId = 30L;

    @BeforeEach
    void setUp() {
        service = new MatchResultService(roomMemberReader, matchResultReader);
    }

    private RoomMember me() {
        return new RoomMember(meId, roomId, userId, "MALE", null, LocalDateTime.now());
    }

    @Test
    @DisplayName("매칭이 있으면 상대 멤버 id를 반환한다(내가 memberA여도 memberB여도 상대를 가리킨다)")
    void givenMatched_whenGet_thenReturnsPartner() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me());
        // me가 memberB인 케이스
        given(matchResultReader.getByMember(meId))
                .willReturn(new MatchResult(40L, partnerId, meId, LocalDateTime.now()));

        MatchResultResponse result = service.getMatchResult(roomId, userId);

        assertThat(result.partnerMemberId()).isEqualTo(partnerId);
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
