package org.son.haruharu.domain.chat.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.dto.ChatRoomDetailResponse;
import org.son.haruharu.domain.chat.business.dto.ChatRoomsResponse;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse;
import org.son.haruharu.domain.chat.business.dto.PartnerBrief;
import org.son.haruharu.domain.chat.business.dto.ReadResponse;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerWithdrawnException;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatMessageWriter;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberWriter;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.ChatRoomWriter;
import org.son.haruharu.domain.chat.storage.exception.ChatRoomNotFoundException;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.user.business.UserService;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.springframework.context.ApplicationEventPublisher;

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
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatRoomReader chatRoomReader;
    @Mock ChatRoomWriter chatRoomWriter;
    @Mock ChatRoomMemberReader chatRoomMemberReader;
    @Mock ChatRoomMemberWriter chatRoomMemberWriter;
    @Mock ChatMessageReader chatMessageReader;
    @Mock ChatMessageWriter chatMessageWriter;
    @Mock ProfileService profileService;
    @Mock UserService userService;
    @Mock ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-03T03:00:00Z"), ZoneOffset.UTC);

    ChatService chatService;

    private static final Long ME = 1L;
    private static final Long ROOM = 10L;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatRoomReader, chatRoomWriter, chatRoomMemberReader,
                chatRoomMemberWriter, chatMessageReader, chatMessageWriter, profileService, userService,
                eventPublisher, clock);
    }

    private ChatRoomMember membership(Long chatRoomId, Long userId, Long lastReadMessageId) {
        return new ChatRoomMember(chatRoomId * 100, chatRoomId, userId, LocalDateTime.now(clock), null, lastReadMessageId);
    }

    private ProfileResponse profile(String nickname) {
        return new ProfileResponse(nickname, "서울", "직장인", 175, "INFP", DrinkingStatus.SOMETIMES, SmokingStatus.NONE, List.of());
    }

    private ProfileResponse profileWithImages(String nickname, ProfileResponse.ImageInfo... images) {
        return new ProfileResponse(nickname, "서울", "직장인", 175, "INFP", DrinkingStatus.SOMETIMES, SmokingStatus.NONE, List.of(images));
    }

    private ChatMessage message(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime at) {
        return new ChatMessage(id, chatRoomId, senderId, content, at);
    }

    private ChatRoom directRoom(Long chatRoomId) {
        return new ChatRoom(chatRoomId, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock));
    }

    private ChatRoom groupRoom(Long chatRoomId, Long roomId) {
        return new ChatRoom(chatRoomId, ChatRoomType.GROUP, roomId, null, LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("내 채팅방 목록은 상대 닉네임·마지막 메시지·안읽은 수를 포함하고 마지막 메시지 최신순으로 정렬된다")
    void getMyRooms_returnsSortedSummaries() {
        LocalDateTime older = LocalDateTime.of(2026, 7, 3, 10, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 7, 3, 11, 0);
        given(chatRoomMemberReader.findActiveByUserId(ME))
                .willReturn(List.of(membership(20L, ME, null), membership(10L, ME, 5L)));
        given(chatRoomReader.getById(20L)).willReturn(directRoom(20L));
        given(chatRoomReader.getById(10L)).willReturn(directRoom(10L));
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
    @DisplayName("채팅방 목록의 상대 정보에 대표사진(sorted_order 최소) URL이 담긴다")
    void getMyRooms_includesPartnerRepresentativeImage() {
        given(chatRoomMemberReader.findActiveByUserId(ME))
                .willReturn(List.of(membership(10L, ME, null)));
        given(chatRoomReader.getById(10L)).willReturn(directRoom(10L));
        given(chatRoomMemberReader.findByChatRoomId(10L))
                .willReturn(List.of(membership(10L, ME, null), membership(10L, 2L, null)));
        // sorted_order가 뒤섞여 있어도 최소값(0)인 이미지가 대표로 선택된다
        given(profileService.getProfile(2L)).willReturn(profileWithImages("하늘",
                new ProfileResponse.ImageInfo(31L, "https://cdn/second.jpg", 2),
                new ProfileResponse.ImageInfo(30L, "https://cdn/rep.jpg", 0),
                new ProfileResponse.ImageInfo(32L, "https://cdn/third.jpg", 1)));
        given(chatMessageReader.findLast(10L)).willReturn(Optional.empty());
        given(chatMessageReader.countUnread(10L, null, ME)).willReturn(0L);

        ChatRoomsResponse result = chatService.getMyRooms(ME);

        PartnerBrief partner = result.chatRooms().get(0).partner();
        assertThat(partner.nickname()).isEqualTo("하늘");
        assertThat(partner.profileImageUrl()).isEqualTo("https://cdn/rep.jpg");
    }

    @Test
    @DisplayName("방 상세(헤더) 조회 시 상대 대표사진 URL이 담긴다")
    void getRoom_includesPartnerRepresentativeImage() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(profileService.getProfile(2L)).willReturn(profileWithImages("하늘",
                new ProfileResponse.ImageInfo(30L, "https://cdn/rep.jpg", 0),
                new ProfileResponse.ImageInfo(31L, "https://cdn/second.jpg", 1)));

        ChatRoomDetailResponse result = chatService.getRoom(ROOM, ME);

        assertThat(result.partner().nickname()).isEqualTo("하늘");
        assertThat(result.partner().profileImageUrl()).isEqualTo("https://cdn/rep.jpg");
    }

    @Test
    @DisplayName("상대가 프로필 이미지가 없으면 대표사진 URL은 null이다")
    void partnerBrief_whenNoImages_profileImageUrlIsNull() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(profileService.getProfile(2L)).willReturn(profile("하늘"));

        ChatRoomDetailResponse result = chatService.getRoom(ROOM, ME);

        assertThat(result.partner().profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("참여자가 아닌 유저가 방 조회 시 ChatNotMemberException(403)이 발생한다")
    void getRoom_whenNotMember_throwsForbidden() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
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
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(left));

        assertThatThrownBy(() -> chatService.getRoom(ROOM, ME))
                .isInstanceOf(ChatNotMemberException.class);
    }

    @Test
    @DisplayName("참여자가 메시지 조회 시 커서 페이지를 반환한다")
    void getMessages_whenMember_returnsPage() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
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
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, 3L)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, 3L), membership(ROOM, 2L, null)));
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
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, 10L)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, 10L), membership(ROOM, 2L, null)));
        given(chatRoomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        chatService.markRead(ROOM, ME, 5L);

        ArgumentCaptor<ChatRoomMember> captor = ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberWriter).save(captor.capture());
        assertThat(captor.getValue().lastReadMessageId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("방 나가기 시 leftAt이 현재 시각으로 기록된다")
    void leaveRoom_recordsLeftAt() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        chatService.leaveRoom(ROOM, ME);

        ArgumentCaptor<ChatRoomMember> captor = ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberWriter).save(captor.capture());
        assertThat(captor.getValue().leftAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("참여자가 메시지 전송 시 저장 후 브로드캐스트 payload를 반환하고 수신자에게 CHAT 이벤트를 발행한다")
    void sendMessage_whenMember_savesReturnsBroadcastAndPublishesEvent() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatMessageWriter.save(any()))
                .willReturn(new ChatMessage(42L, ROOM, ME, "안녕", LocalDateTime.now(clock)));
        // 상대(2L) 계산용
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(profileService.getNickname(ME)).willReturn("하루");

        ChatMessageBroadcast result = chatService.sendMessage(ROOM, ME, "안녕");

        assertThat(result.messageId()).isEqualTo(42L);
        assertThat(result.senderId()).isEqualTo(ME);
        assertThat(result.content()).isEqualTo("안녕");
        then(eventPublisher).should().publishEvent(new ChatMessageSentEvent(ROOM, List.of(2L), "하루"));
    }

    @Test
    @DisplayName("참여자가 아닌 유저가 메시지 전송 시 ChatNotMemberException이 발생하고 저장·이벤트 발행을 하지 않는다")
    void sendMessage_whenNotMember_throwsAndDoesNotSaveOrPublish() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(ROOM, ME, "안녕"))
                .isInstanceOf(ChatNotMemberException.class);
        then(chatMessageWriter).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("상대가 탈퇴한 방은 목록에서 탈퇴 표시만 되고 상대 정보·마지막 메시지가 비워진다")
    void givenWithdrawnPartner_whenGetMyRooms_thenReturnsWithdrawnSummary() {
        given(chatRoomMemberReader.findActiveByUserId(ME))
                .willReturn(List.of(membership(ROOM, ME, null)));
        given(chatRoomReader.getById(ROOM)).willReturn(directRoom(ROOM));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(userService.isWithdrawn(2L)).willReturn(true);

        ChatRoomsResponse result = chatService.getMyRooms(ME);

        ChatRoomsResponse.ChatRoomSummary summary = result.chatRooms().get(0);
        assertThat(summary.chatRoomId()).isEqualTo(ROOM);
        assertThat(summary.partnerWithdrawn()).isTrue();
        assertThat(summary.partner()).isNull();
        assertThat(summary.lastMessage()).isNull();
        assertThat(summary.lastMessageAt()).isNull();
        assertThat(summary.unreadCount()).isZero();
        // 탈퇴자 프로필은 soft-delete로 조회 불가 — 아예 건드리지 않아야 404가 나지 않는다
        then(profileService).should(never()).getProfile(any());
    }

    @Test
    @DisplayName("상대가 탈퇴해도 정상인 다른 방은 목록에 그대로 노출된다")
    void givenWithdrawnPartner_whenGetMyRooms_thenOtherRoomsStillReturned() {
        given(chatRoomMemberReader.findActiveByUserId(ME))
                .willReturn(List.of(membership(20L, ME, null), membership(10L, ME, null)));
        given(chatRoomReader.getById(20L)).willReturn(directRoom(20L));
        given(chatRoomReader.getById(10L)).willReturn(directRoom(10L));
        given(chatRoomMemberReader.findByChatRoomId(20L))
                .willReturn(List.of(membership(20L, ME, null), membership(20L, 3L, null)));
        given(chatRoomMemberReader.findByChatRoomId(10L))
                .willReturn(List.of(membership(10L, ME, null), membership(10L, 2L, null)));
        given(userService.isWithdrawn(3L)).willReturn(true);
        given(userService.isWithdrawn(2L)).willReturn(false);
        given(profileService.getProfile(2L)).willReturn(profile("하늘"));
        given(chatMessageReader.findLast(10L))
                .willReturn(Optional.of(message(8L, 10L, 2L, "안녕", LocalDateTime.of(2026, 7, 3, 11, 0))));
        given(chatMessageReader.countUnread(10L, null, ME)).willReturn(2L);

        ChatRoomsResponse result = chatService.getMyRooms(ME);

        assertThat(result.chatRooms()).hasSize(2);
        // 마지막 메시지가 있는 정상 방이 먼저, 탈퇴 방(lastMessageAt=null)은 뒤로
        ChatRoomsResponse.ChatRoomSummary active = result.chatRooms().get(0);
        assertThat(active.chatRoomId()).isEqualTo(10L);
        assertThat(active.partnerWithdrawn()).isFalse();
        assertThat(active.partner().nickname()).isEqualTo("하늘");
        assertThat(result.chatRooms().get(1).partnerWithdrawn()).isTrue();
    }

    @Test
    @DisplayName("상대가 탈퇴한 방에 진입하면 ChatPartnerWithdrawnException(403)이 발생한다")
    void givenWithdrawnPartner_whenGetRoom_thenThrowsForbidden() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(userService.isWithdrawn(2L)).willReturn(true);

        assertThatThrownBy(() -> chatService.getRoom(ROOM, ME))
                .isInstanceOf(ChatPartnerWithdrawnException.class);
    }

    @Test
    @DisplayName("상대가 탈퇴한 방의 메시지는 조회할 수 없다")
    void givenWithdrawnPartner_whenGetMessages_thenThrowsForbidden() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(userService.isWithdrawn(2L)).willReturn(true);

        assertThatThrownBy(() -> chatService.getMessages(ROOM, ME, null, 30))
                .isInstanceOf(ChatPartnerWithdrawnException.class);
    }

    @Test
    @DisplayName("상대가 탈퇴한 방은 읽음 처리를 할 수 없다")
    void givenWithdrawnPartner_whenMarkRead_thenThrowsForbidden() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(userService.isWithdrawn(2L)).willReturn(true);

        assertThatThrownBy(() -> chatService.markRead(ROOM, ME, 7L))
                .isInstanceOf(ChatPartnerWithdrawnException.class);
        then(chatRoomMemberWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("상대가 탈퇴한 방에는 메시지를 전송할 수 없다")
    void givenWithdrawnPartner_whenSendMessage_thenThrowsAndDoesNotSaveOrPublish() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(userService.isWithdrawn(2L)).willReturn(true);

        assertThatThrownBy(() -> chatService.sendMessage(ROOM, ME, "안녕"))
                .isInstanceOf(ChatPartnerWithdrawnException.class);
        then(chatMessageWriter).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("상대가 탈퇴한 방도 나가기는 가능하다 — 목록에서 치울 수단이 필요하다")
    void givenWithdrawnPartner_whenLeaveRoom_thenSucceeds() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        chatService.leaveRoom(ROOM, ME);

        ArgumentCaptor<ChatRoomMember> captor = ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberWriter).save(captor.capture());
        assertThat(captor.getValue().leftAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("상호매칭 성사 시 DIRECT 채팅방과 두 참여자를 생성한다")
    void createRoomFromMatch_createsRoomAndTwoMembers() {
        given(chatRoomReader.existsByMatchResultId(500L)).willReturn(false);
        given(chatRoomWriter.save(any())).willReturn(new ChatRoom(77L, ChatRoomType.DIRECT, null, 500L, LocalDateTime.now(clock)));

        chatService.createRoomFromMatch(500L, 1L, 2L);

        ArgumentCaptor<ChatRoom> roomCaptor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomWriter).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().type()).isEqualTo(ChatRoomType.DIRECT);
        ArgumentCaptor<ChatRoomMember> captor = ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberWriter, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ChatRoomMember::userId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(captor.getAllValues()).allSatisfy(m -> assertThat(m.chatRoomId()).isEqualTo(77L));
    }

    @Test
    @DisplayName("이미 방이 있는 매칭이면 중복 생성하지 않는다(멱등)")
    void createRoomFromMatch_whenAlreadyExists_skips() {
        given(chatRoomReader.existsByMatchResultId(500L)).willReturn(true);

        chatService.createRoomFromMatch(500L, 1L, 2L);

        then(chatRoomWriter).should(never()).save(any());
        then(chatRoomMemberWriter).should(never()).save(any());
    }

    // ── GROUP 채팅 ──────────────────────────────────────────

    @Test
    @DisplayName("방 시작 시 GROUP 채팅방과 멤버 전원(6명 스냅샷)을 생성한다")
    void createGroupRoom_createsGroupRoomAndAllMembers() {
        given(chatRoomReader.existsByRoomId(ROOM)).willReturn(false);
        given(chatRoomWriter.save(any())).willReturn(groupRoom(88L, ROOM));

        chatService.createGroupRoom(ROOM, List.of(1L, 2L, 3L, 4L, 5L, 6L));

        ArgumentCaptor<ChatRoom> roomCaptor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomWriter).save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().type()).isEqualTo(ChatRoomType.GROUP);
        assertThat(roomCaptor.getValue().roomId()).isEqualTo(ROOM);
        ArgumentCaptor<ChatRoomMember> captor = ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberWriter, times(6)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ChatRoomMember::userId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(captor.getAllValues()).allSatisfy(m -> assertThat(m.chatRoomId()).isEqualTo(88L));
    }

    @Test
    @DisplayName("이미 GROUP 채팅이 있는 방이면 중복 생성하지 않는다(멱등, room_id 기준)")
    void createGroupRoom_whenAlreadyExists_skips() {
        given(chatRoomReader.existsByRoomId(ROOM)).willReturn(true);

        chatService.createGroupRoom(ROOM, List.of(1L, 2L, 3L));

        then(chatRoomWriter).should(never()).save(any());
        then(chatRoomMemberWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("GROUP 채팅 상세는 상대 단수가 아니라 나 제외 멤버 목록을 반환한다")
    void getRoom_whenGroup_returnsMemberList() {
        given(chatRoomReader.getById(ROOM)).willReturn(groupRoom(ROOM, 99L));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM)).willReturn(List.of(
                membership(ROOM, ME, null), membership(ROOM, 2L, null), membership(ROOM, 3L, null)));
        given(userService.isWithdrawn(2L)).willReturn(false);
        given(userService.isWithdrawn(3L)).willReturn(false);
        given(profileService.getProfile(2L)).willReturn(profile("바다"));
        given(profileService.getProfile(3L)).willReturn(profile("하늘"));

        ChatRoomDetailResponse result = chatService.getRoom(ROOM, ME);

        assertThat(result.type()).isEqualTo("GROUP");
        assertThat(result.partner()).isNull();
        assertThat(result.members()).extracting(PartnerBrief::userId).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("GROUP 메시지 전송 시 발신자 제외 전원에게 CHAT 이벤트를 발행한다")
    void sendMessage_whenGroup_publishesEventToAllOthers() {
        given(chatRoomReader.getById(ROOM)).willReturn(groupRoom(ROOM, 99L));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatMessageWriter.save(any()))
                .willReturn(new ChatMessage(42L, ROOM, ME, "안녕", LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM)).willReturn(List.of(
                membership(ROOM, ME, null), membership(ROOM, 2L, null), membership(ROOM, 3L, null)));
        given(profileService.getNickname(ME)).willReturn("하루");

        chatService.sendMessage(ROOM, ME, "안녕");

        ArgumentCaptor<ChatMessageSentEvent> captor = ArgumentCaptor.forClass(ChatMessageSentEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().recipientUserIds()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("GROUP 메시지 수신자에서 방을 나간 멤버(leftAt)는 제외된다")
    void sendMessage_whenGroup_excludesLeftMembers() {
        given(chatRoomReader.getById(ROOM)).willReturn(groupRoom(ROOM, 99L));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatMessageWriter.save(any()))
                .willReturn(new ChatMessage(42L, ROOM, ME, "안녕", LocalDateTime.now(clock)));
        ChatRoomMember leftMember = membership(ROOM, 3L, null).leave(LocalDateTime.now(clock));
        given(chatRoomMemberReader.findByChatRoomId(ROOM)).willReturn(List.of(
                membership(ROOM, ME, null), membership(ROOM, 2L, null), leftMember));
        given(profileService.getNickname(ME)).willReturn("하루");

        chatService.sendMessage(ROOM, ME, "안녕");

        ArgumentCaptor<ChatMessageSentEvent> captor = ArgumentCaptor.forClass(ChatMessageSentEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().recipientUserIds()).containsExactly(2L); // 나간 3L 제외
    }
}
