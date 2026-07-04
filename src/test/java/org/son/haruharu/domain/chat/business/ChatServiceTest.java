package org.son.haruharu.domain.chat.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.business.dto.ChatRoomsResponse;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse;
import org.son.haruharu.domain.chat.business.dto.ReadResponse;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberWriter;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.storage.exception.ChatRoomNotFoundException;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatRoomReader chatRoomReader;
    @Mock ChatRoomMemberReader chatRoomMemberReader;
    @Mock ChatRoomMemberWriter chatRoomMemberWriter;
    @Mock ChatMessageReader chatMessageReader;
    @Mock ProfileService profileService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-03T03:00:00Z"), ZoneOffset.UTC);

    ChatService chatService;

    private static final Long ME = 1L;
    private static final Long ROOM = 10L;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatRoomReader, chatRoomMemberReader, chatRoomMemberWriter,
                chatMessageReader, profileService, clock);
    }

    private ChatRoomMember membership(Long chatRoomId, Long userId, Long lastReadMessageId) {
        return new ChatRoomMember(chatRoomId * 100, chatRoomId, userId, LocalDateTime.now(clock), null, lastReadMessageId);
    }

    private ProfileResponse profile(String nickname) {
        return new ProfileResponse(nickname, "서울", "직장인", 175, "INFP", "가끔", "비흡연", List.of());
    }

    private ChatMessage message(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime at) {
        return new ChatMessage(id, chatRoomId, senderId, content, at);
    }

    @Test
    @DisplayName("내 채팅방 목록은 상대 닉네임·마지막 메시지·안읽은 수를 포함하고 마지막 메시지 최신순으로 정렬된다")
    void getMyRooms_returnsSortedSummaries() {
        LocalDateTime older = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 7, 3, 11, 0);
        given(chatRoomMemberReader.findActiveByUserId(ME))
                .willReturn(List.of(membership(20L, ME, null), membership(10L, ME, 5L)));
        // 상대 찾기
        given(chatRoomMemberReader.findByChatRoomId(20L))
                .willReturn(List.of(membership(20L, ME, null), membership(20L, 3L, null)));
        given(chatRoomMemberReader.findByChatRoomId(10L))
                .willReturn(List.of(membership(10L, ME, 5L), membership(10L, 2L, null)));
        given(profileService.getProfile(3L)).willReturn(profile("바다"));
        given(profileService.getProfile(2L)).willReturn(profile("하늘"));
        given(chatMessageReader.findLast(20L)).willReturn(Optional.of(message(3L, 20L, 3L, "헤이", older)));
        given(chatMessageReader.findLast(10L)).willReturn(Optional.of(message(8L, 10L, 2L, "안녕", newer)));
        given(chatMessageReader.countUnread(20L, null, ME)).willReturn(1L);
        given(chatMessageReader.countUnread(10L, 5L, ME)).willReturn(2L);

        ChatRoomsResponse result = chatService.getMyRooms(ME);

        assertThat(result.chatRooms()).hasSize(2);
        ChatRoomsResponse.ChatRoomSummary first = result.chatRooms().get(0);
        assertThat(first.chatRoomId()).isEqualTo(10L);
        assertThat(first.partner().nickname()).isEqualTo("하늘");
        assertThat(first.lastMessage()).isEqualTo("안녕");
        assertThat(first.lastMessageAt()).isEqualTo(newer);
        assertThat(first.unreadCount()).isEqualTo(2L);
        assertThat(result.chatRooms().get(1).chatRoomId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("참여자가 아닌 유저가 방 조회 시 ChatNotMemberException(403)이 발생한다")
    void getRoom_whenNotMember_throwsForbidden() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getRoom(ROOM, ME))
                .isInstanceOf(ChatNotMemberException.class);
    }

    @Test
    @DisplayName("존재하지 않는 방 조회 시 ChatRoomNotFoundException(404)이 전파된다")
    void getRoom_whenRoomNotFound_throwsNotFound() {
        given(chatRoomReader.getById(ROOM)).willThrow(new ChatRoomNotFoundException());

        assertThatThrownBy(() -> chatService.getRoom(ROOM, ME))
                .isInstanceOf(ChatRoomNotFoundException.class);
    }

    @Test
    @DisplayName("방을 나간 멤버가 접근 시 ChatNotMemberException(403)이 발생한다")
    void getRoom_whenLeftMember_throwsForbidden() {
        ChatRoomMember left = membership(ROOM, ME, null).leave(LocalDateTime.now(clock));
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(left));

        assertThatThrownBy(() -> chatService.getRoom(ROOM, ME))
                .isInstanceOf(ChatNotMemberException.class);
    }

    @Test
    @DisplayName("참여자가 메시지 조회 시 커서 페이지를 반환한다")
    void getMessages_whenMember_returnsPage() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatMessageReader.findPage(ROOM, null, 30))
                .willReturn(List.of(message(8L, ROOM, 2L, "b", LocalDateTime.now(clock)),
                        message(7L, ROOM, 1L, "a", LocalDateTime.now(clock))));

        MessagePageResponse result = chatService.getMessages(ROOM, ME, null, 30);

        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(0).messageId()).isEqualTo(8L);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("읽음 처리 시 커서가 전진 저장되고 안읽은 수를 반환한다")
    void markRead_advancesCursorAndReturnsUnread() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, 3L)));
        given(chatRoomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(chatMessageReader.countUnread(ROOM, 7L, ME)).willReturn(0L);

        ReadResponse result = chatService.markRead(ROOM, ME, 7L);

        ArgumentCaptor<ChatRoomMember> captor = ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberWriter).save(captor.capture());
        assertThat(captor.getValue().lastReadMessageId()).isEqualTo(7L);
        assertThat(result.unreadCount()).isZero();
    }

    @Test
    @DisplayName("읽음 처리에서 과거 메시지 id를 보내도 커서가 역행하지 않는다")
    void markRead_doesNotMoveCursorBackward() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, 10L)));
        given(chatRoomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        chatService.markRead(ROOM, ME, 5L);

        ArgumentCaptor<ChatRoomMember> captor = ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberWriter).save(captor.capture());
        assertThat(captor.getValue().lastReadMessageId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("방 나가기 시 leftAt이 현재 시각으로 기록된다")
    void leaveRoom_recordsLeftAt() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        chatService.leaveRoom(ROOM, ME);

        ArgumentCaptor<ChatRoomMember> captor = ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberWriter).save(captor.capture());
        assertThat(captor.getValue().leftAt()).isEqualTo(LocalDateTime.now(clock));
    }
}
