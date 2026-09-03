package org.son.haruharu.domain.chat.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatRoomReader chatRoomReader;
    @Mock ChatRoomWriter chatRoomWriter;
    @Mock ChatRoomMemberReader chatRoomMemberReader;
    @Mock ChatRoomMemberWriter chatRoomMemberWriter;
    @Mock ChatMessageReader chatMessageReader;
    @Mock ChatMessageWriter chatMessageWriter;
    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
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
                chatRoomMemberWriter, chatMessageReader, chatMessageWriter, roomReader, roomMemberReader,
                profileService, userService, eventPublisher, clock);
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

    private Room room(RoomStatus status) {
        return new Room(ROOM, status, LocalDate.of(2026, 7, 3), 3,
                status == RoomStatus.ACTIVE ? LocalDateTime.now(clock) : null, null);
    }

    private RoomMember roomMember(Long id, Long userId, LocalDateTime joinedAt) {
        return new RoomMember(id, ROOM, userId, null, null, joinedAt);
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
        given(chatMessageReader.findLast(20L, LocalDateTime.now(clock)))
                .willReturn(Optional.of(message(3L, 20L, 3L, "헤이", older)));
        given(chatMessageReader.findLast(10L, LocalDateTime.now(clock)))
                .willReturn(Optional.of(message(8L, 10L, 2L, "안녕", newer)));
        given(chatMessageReader.countUnread(20L, null, ME, LocalDateTime.now(clock))).willReturn(1L);
        given(chatMessageReader.countUnread(10L, 5L, ME, LocalDateTime.now(clock))).willReturn(2L);

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
        given(chatMessageReader.findLast(10L, LocalDateTime.now(clock))).willReturn(Optional.empty());
        given(chatMessageReader.countUnread(10L, null, ME, LocalDateTime.now(clock))).willReturn(0L);

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
    @DisplayName("메시지 조회는 membership 입장 시각을 inclusive 하한으로 reader에 전달한다")
    void givenMembershipJoinTime_whenGetMessages_thenPassesInclusiveBoundaryToReader() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(chatMessageReader.findPage(ROOM, LocalDateTime.now(clock), null, 30))
                .willReturn(List.of(message(8L, ROOM, 2L, "b", LocalDateTime.now(clock)),
                        message(7L, ROOM, 1L, "a", LocalDateTime.now(clock))));

        MessagePageResponse result = chatService.getMessages(ROOM, ME, null, 30);

        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(0).messageId()).isEqualTo(8L);
        assertThat(result.nextCursor()).isNull();
        then(chatMessageReader).should().findPage(ROOM, LocalDateTime.now(clock), null, 30);
    }

    @Test
    @DisplayName("채팅방 목록은 membership 입장 시각을 마지막 메시지와 unread 쿼리에 전달한다")
    void givenMembershipJoinTime_whenGetMyRooms_thenPassesBoundaryToLastAndUnreadQueries() {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 7, 3, 10, 30);
        ChatRoomMember me = new ChatRoomMember(1L, ROOM, ME, joinedAt, null, 7L);
        given(chatRoomMemberReader.findActiveByUserId(ME)).willReturn(List.of(me));
        given(chatRoomReader.getById(ROOM)).willReturn(groupRoom(ROOM, 99L));
        given(chatMessageReader.findLast(ROOM, joinedAt)).willReturn(Optional.empty());
        given(chatMessageReader.countUnread(ROOM, 7L, ME, joinedAt)).willReturn(0L);
        given(chatRoomMemberReader.findByChatRoomId(ROOM)).willReturn(List.of(me));

        chatService.getMyRooms(ME);

        then(chatMessageReader).should().findLast(ROOM, joinedAt);
        then(chatMessageReader).should().countUnread(ROOM, 7L, ME, joinedAt);
    }

    @Test
    @DisplayName("전체 unread 집계는 단일 count 결과를 반환하고 프로필·유저 요약을 구축하지 않는다")
    void givenAggregateUnread_whenGetUnreadCount_thenReturnsCountWithoutBuildingSummariesOrProfiles() {
        given(chatMessageReader.countUnreadByUserId(ME)).willReturn(7L);

        var result = chatService.getUnreadCount(ME);

        assertThat(result.unreadCount()).isEqualTo(7L);
        verifyNoInteractions(profileService, userService);
    }

    @Test
    @DisplayName("읽음 처리 시 커서가 전진 저장되고 안읽은 수를 반환한다")
    void markRead_advancesCursorAndReturnsUnread() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, 3L)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, 3L), membership(ROOM, 2L, null)));
        given(chatRoomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(chatMessageReader.countUnread(ROOM, 7L, ME, LocalDateTime.now(clock))).willReturn(0L);

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
        then(eventPublisher).should().publishEvent(new ChatMessageSentEvent(
                42L, ROOM, ChatRoomType.DIRECT, null, ME,
                List.of(2L), "하루", LocalDateTime.now(clock)));
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
        given(chatMessageReader.findLast(10L, LocalDateTime.now(clock)))
                .willReturn(Optional.of(message(8L, 10L, 2L, "안녕", LocalDateTime.of(2026, 7, 3, 11, 0))));
        given(chatMessageReader.countUnread(10L, null, ME, LocalDateTime.now(clock))).willReturn(2L);

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
    @DisplayName("PENDING 방은 GROUP 채팅 행을 잠그거나 생성하지 않는다")
    void givenPendingRoom_whenSynchronizeGroupRoom_thenDoesNotCreateOrLockChatRows() {
        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(room(RoomStatus.PENDING)));

        chatService.synchronizeGroupRoom(ROOM);

        then(chatRoomReader).shouldHaveNoInteractions();
        then(chatRoomWriter).shouldHaveNoInteractions();
        then(chatRoomMemberReader).shouldHaveNoInteractions();
        then(chatRoomMemberWriter).shouldHaveNoInteractions();
        then(roomMemberReader).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ACTIVE 방은 GROUP 채팅을 만들고 room member 입장 시각을 정확히 복사한다")
    void givenActiveRoomWithoutGroupChat_whenSynchronizeGroupRoom_thenCreatesChatAndCopiesAllRoomMemberJoinTimes() {
        LocalDateTime firstJoinedAt = LocalDateTime.of(2026, 7, 3, 9, 15);
        LocalDateTime secondJoinedAt = LocalDateTime.of(2026, 7, 3, 10, 45);
        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(room(RoomStatus.ACTIVE)));
        given(chatRoomReader.findByRoomIdForUpdate(ROOM)).willReturn(Optional.empty());
        given(chatRoomWriter.save(any())).willReturn(groupRoom(88L, ROOM));
        given(chatRoomMemberReader.findByChatRoomIdForUpdate(88L)).willReturn(List.of());
        given(roomMemberReader.findByRoomIdOrdered(ROOM)).willReturn(List.of(
                roomMember(11L, 101L, firstJoinedAt),
                roomMember(12L, 102L, secondJoinedAt)));

        chatService.synchronizeGroupRoom(ROOM);

        InOrder order = inOrder(roomReader, chatRoomReader, chatRoomWriter,
                chatRoomMemberReader, roomMemberReader, chatRoomMemberWriter);
        order.verify(roomReader).findByIdForUpdate(ROOM);
        order.verify(chatRoomReader).findByRoomIdForUpdate(ROOM);
        order.verify(chatRoomWriter).save(any());
        order.verify(chatRoomMemberReader).findByChatRoomIdForUpdate(88L);
        order.verify(roomMemberReader).findByRoomIdOrdered(ROOM);
        ArgumentCaptor<ChatRoomMember> memberCaptor = ArgumentCaptor.forClass(ChatRoomMember.class);
        order.verify(chatRoomMemberWriter, times(2)).save(memberCaptor.capture());
        assertThat(memberCaptor.getAllValues())
                .extracting(ChatRoomMember::userId, ChatRoomMember::joinedAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(101L, firstJoinedAt),
                        org.assertj.core.groups.Tuple.tuple(102L, secondJoinedAt));
    }

    @Test
    @DisplayName("SELECTING 방은 left 행을 보존하고 누락된 멤버만 추가한다")
    void givenSelectingRoomWithMissingChatMember_whenSynchronizeGroupRoom_thenAddsOnlyMissingMember() {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 7, 3, 11, 30);
        ChatRoomMember existing = membership(88L, 101L, null);
        ChatRoomMember left = membership(88L, 102L, null).leave(LocalDateTime.of(2026, 7, 3, 12, 0));
        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(room(RoomStatus.SELECTING)));
        given(chatRoomReader.findByRoomIdForUpdate(ROOM)).willReturn(Optional.of(groupRoom(88L, ROOM)));
        given(chatRoomMemberReader.findByChatRoomIdForUpdate(88L)).willReturn(List.of(existing, left));
        given(roomMemberReader.findByRoomIdOrdered(ROOM)).willReturn(List.of(
                roomMember(11L, 101L, LocalDateTime.of(2026, 7, 3, 9, 0)),
                roomMember(12L, 102L, LocalDateTime.of(2026, 7, 3, 9, 5)),
                roomMember(13L, 103L, joinedAt)));

        chatService.synchronizeGroupRoom(ROOM);

        ArgumentCaptor<ChatRoomMember> captor = ArgumentCaptor.forClass(ChatRoomMember.class);
        then(chatRoomMemberWriter).should().save(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(103L);
        assertThat(captor.getValue().joinedAt()).isEqualTo(joinedAt);
        then(chatRoomWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("중복 동기화는 이미 있는 membership을 다시 저장하지 않는다")
    void givenDuplicateSynchronization_whenMembershipExists_thenDoesNotInsertAgain() {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 7, 3, 9, 15);
        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(room(RoomStatus.ACTIVE)));
        given(chatRoomReader.findByRoomIdForUpdate(ROOM)).willReturn(Optional.of(groupRoom(88L, ROOM)));
        given(chatRoomMemberReader.findByChatRoomIdForUpdate(88L))
                .willReturn(List.of(new ChatRoomMember(1L, 88L, 101L, joinedAt, null, null)));
        given(roomMemberReader.findByRoomIdOrdered(ROOM))
                .willReturn(List.of(roomMember(11L, 101L, joinedAt)));

        chatService.synchronizeGroupRoom(ROOM);

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
