package org.son.haruharu.domain.chat.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.dto.PairChatWindowResponse;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerBlockedException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerWithdrawnException;
import org.son.haruharu.domain.chat.business.exception.PairChatInvalidTargetException;
import org.son.haruharu.domain.chat.business.exception.PairChatNotOpenException;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberWriter;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.chat.implement.ChatRoomWriter;
import org.son.haruharu.domain.chat.implement.PairChatSchedule;
import org.son.haruharu.domain.chat.implement.PairChatWindow;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.user.business.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PairChatServiceTest {

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock ChatRoomReader chatRoomReader;
    @Mock ChatRoomWriter chatRoomWriter;
    @Mock ChatRoomMemberWriter chatRoomMemberWriter;
    @Mock PairChatSchedule pairChatSchedule;
    @Mock BlockReader blockReader;
    @Mock UserService userService;

    final Clock clock = Clock.fixed(Instant.parse("2026-10-28T11:10:00Z"), ZoneOffset.UTC);
    final LocalDateTime now = LocalDateTime.now(clock);
    static final Long ROOM = 104L;
    static final Long ME = 1L;
    static final Long PARTNER = 2L;
    static final Long PARTNER_MEMBER = 20L;

    PairChatService service;

    @BeforeEach
    void setUp() {
        service = new PairChatService(roomReader, roomMemberReader, chatRoomReader, chatRoomWriter,
                chatRoomMemberWriter, pairChatSchedule, blockReader, userService, clock);
    }

    RoomMember member(Long id, Long roomId, Long userId, String pseudonym) {
        return new RoomMember(id, roomId, userId, "FEMALE", pseudonym, null, now.minusDays(1));
    }

    void givenParticipantAndTarget() {
        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(mock(Room.class)));
        given(roomMemberReader.getByRoomIdAndUserId(ROOM, ME)).willReturn(member(10L, ROOM, ME, "하늘"));
        given(roomMemberReader.findById(PARTNER_MEMBER)).willReturn(Optional.of(member(PARTNER_MEMBER, ROOM, PARTNER, "서연")));
    }

    void givenOpenWindow() {
        given(pairChatSchedule.windowAt(ROOM, now)).willReturn(PairChatWindow.open(now.minusMinutes(10), 1, now.plusHours(1)));
    }

    @Test
    @DisplayName("대화 시간 중 처음 열면 원본 방 id 를 가진 PAIR 채팅방과 두 사람 멤버를 만든다")
    void givenOpenWindow_whenOpenFirstTime_thenCreatesPairRoom() {
        givenParticipantAndTarget();
        givenOpenWindow();
        given(chatRoomReader.findPair(ROOM, ME, PARTNER)).willReturn(Optional.empty());
        given(chatRoomWriter.save(any())).willReturn(new ChatRoom(345L, ChatRoomType.PAIR, ROOM, null, now));

        var result = service.open(ROOM, ME, PARTNER_MEMBER);

        assertThat(result.chatRoomId()).isEqualTo(345L);
        ArgumentCaptor<ChatRoom> room = ArgumentCaptor.forClass(ChatRoom.class);
        then(chatRoomWriter).should().save(room.capture());
        assertThat(room.getValue().type()).isEqualTo(ChatRoomType.PAIR);
        assertThat(room.getValue().roomId()).isEqualTo(ROOM);
        then(chatRoomMemberWriter).should().save(ChatRoomMember.join(345L, ME, now));
        then(chatRoomMemberWriter).should().save(ChatRoomMember.join(345L, PARTNER, now));
    }

    @Test
    @DisplayName("같은 두 사람이 다시 열면 기존 방을 돌려주고 새로 만들지 않는다")
    void givenExistingPair_whenOpen_thenReturnsSameRoom() {
        givenParticipantAndTarget();
        givenOpenWindow();
        given(chatRoomReader.findPair(ROOM, ME, PARTNER))
                .willReturn(Optional.of(new ChatRoom(345L, ChatRoomType.PAIR, ROOM, null, now.minusDays(1))));

        assertThat(service.open(ROOM, ME, PARTNER_MEMBER).chatRoomId()).isEqualTo(345L);
        then(chatRoomWriter).should(never()).save(any());
        then(chatRoomMemberWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("대화 시간이 아니면 PAIR_CHAT_NOT_OPEN 으로 거절하고 아무것도 만들지 않는다")
    void givenWindowNotOpen_whenOpen_thenRejected() {
        givenParticipantAndTarget();
        given(pairChatSchedule.windowAt(ROOM, now)).willReturn(PairChatWindow.scheduled(now.plusHours(1), 1, now.plusHours(3)));

        assertThatThrownBy(() -> service.open(ROOM, ME, PARTNER_MEMBER)).isInstanceOf(PairChatNotOpenException.class);
        then(chatRoomWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("나 자신·다른 방 멤버·없는 멤버는 PAIR_CHAT_INVALID_TARGET")
    void givenInvalidTarget_whenOpen_thenRejected() {
        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(mock(Room.class)));
        given(roomMemberReader.getByRoomIdAndUserId(ROOM, ME)).willReturn(member(10L, ROOM, ME, "하늘"));
        given(roomMemberReader.findById(10L)).willReturn(Optional.of(member(10L, ROOM, ME, "하늘")));
        given(roomMemberReader.findById(99L)).willReturn(Optional.of(member(99L, 999L, 9L, "다른 방")));
        given(roomMemberReader.findById(98L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(ROOM, ME, 10L)).isInstanceOf(PairChatInvalidTargetException.class);
        assertThatThrownBy(() -> service.open(ROOM, ME, 99L)).isInstanceOf(PairChatInvalidTargetException.class);
        assertThatThrownBy(() -> service.open(ROOM, ME, 98L)).isInstanceOf(PairChatInvalidTargetException.class);
        then(pairChatSchedule).shouldHaveNoInteractions();
        then(chatRoomWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("차단 관계(양방향)나 탈퇴한 상대와는 열 수 없다")
    void givenBlockedOrWithdrawnPartner_whenOpen_thenRejected() {
        givenParticipantAndTarget();
        givenOpenWindow();
        given(blockReader.isBlockedBetween(ME, PARTNER)).willReturn(true);
        assertThatThrownBy(() -> service.open(ROOM, ME, PARTNER_MEMBER)).isInstanceOf(ChatPartnerBlockedException.class);

        given(userService.isWithdrawn(PARTNER)).willReturn(true);
        assertThatThrownBy(() -> service.open(ROOM, ME, PARTNER_MEMBER)).isInstanceOf(ChatPartnerWithdrawnException.class);
        then(chatRoomWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("방 참가자가 아니면 대화 시간도, 방 열기도 거절한다")
    void givenNotParticipant_whenWindowOrOpen_thenForbidden() {
        given(roomReader.findById(ROOM)).willReturn(Optional.of(mock(Room.class)));
        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(mock(Room.class)));
        given(roomMemberReader.getByRoomIdAndUserId(ROOM, 7L)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getWindow(ROOM, 7L)).isInstanceOf(NotRoomMemberException.class);
        assertThatThrownBy(() -> service.open(ROOM, 7L, PARTNER_MEMBER)).isInstanceOf(NotRoomMemberException.class);
        then(pairChatSchedule).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("대화 시간 조회는 상태·시작·일차·끝을 그대로 돌려준다")
    void givenScheduledWindow_whenGetWindow_thenMapped() {
        given(roomReader.findById(ROOM)).willReturn(Optional.of(mock(Room.class)));
        given(roomMemberReader.getByRoomIdAndUserId(ROOM, ME)).willReturn(member(10L, ROOM, ME, "하늘"));
        given(pairChatSchedule.windowAt(ROOM, now)).willReturn(PairChatWindow.scheduled(now.plusHours(9), 1, now.plusHours(11)));

        assertThat(service.getWindow(ROOM, ME))
                .isEqualTo(new PairChatWindowResponse("SCHEDULED", now.plusHours(9), 1, now.plusHours(11)));
        then(chatRoomMemberWriter).should(times(0)).save(any());
    }
}
