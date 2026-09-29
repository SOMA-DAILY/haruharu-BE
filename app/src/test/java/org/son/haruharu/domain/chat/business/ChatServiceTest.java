package org.son.haruharu.domain.chat.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.dto.ChatRoomDetailResponse;
import org.son.haruharu.domain.chat.business.dto.ChatRoomsResponse;
import org.son.haruharu.domain.chat.business.dto.GroupChatRoomResponse;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse;
import org.son.haruharu.domain.chat.business.dto.PartnerBrief;
import org.son.haruharu.domain.chat.business.dto.ReadResponse;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerBlockedException;
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
import java.util.Set;

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
    @Mock org.son.haruharu.domain.room.implement.RoomMemberReader roomMemberReader;
    @Mock RoomReader roomReader;
    @Mock BlockReader blockReader;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock org.son.haruharu.domain.chat.implement.ChatMessageReactionReader chatMessageReactionReader;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-03T03:00:00Z"), ZoneOffset.UTC);

    ChatService chatService;

    private static final Long ME = 1L;
    private static final Long ROOM = 10L;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatRoomReader, chatRoomWriter, chatRoomMemberReader,
                chatRoomMemberWriter, chatMessageReader, chatMessageWriter, profileService, userService,
                roomMemberReader, roomReader, blockReader,
                new ChatMessageViews(chatMessageReactionReader, chatMessageReader, blockReader),
                new ChatParticipantPolicy(chatRoomReader, chatRoomMemberReader, roomMemberReader, profileService, userService, blockReader),
                eventPublisher, clock);
    }

    private ChatRoomMember membership(Long chatRoomId, Long userId, Long lastReadMessageId) {
        return new ChatRoomMember(chatRoomId * 100, chatRoomId, userId, LocalDateTime.now(clock), null, lastReadMessageId);
    }

    private ProfileResponse profile(String nickname) {
        return new ProfileResponse(nickname, "서울", "직장인", 175, "INFP", DrinkingStatus.SOMETIMES, SmokingStatus.NONE, List.of(), null);
    }

    private ProfileResponse profileWithImages(String nickname, ProfileResponse.ImageInfo... images) {
        return new ProfileResponse(nickname, "서울", "직장인", 175, "INFP", DrinkingStatus.SOMETIMES, SmokingStatus.NONE, List.of(images), null);
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
        given(chatRoomReader.findById(20L)).willReturn(Optional.of(directRoom(20L)));
        given(chatRoomReader.findById(10L)).willReturn(Optional.of(directRoom(10L)));
        // 상대 찾기
        given(chatRoomMemberReader.findByChatRoomId(20L))
                .willReturn(List.of(membership(20L, ME, null), membership(20L, 3L, null)));
        given(chatRoomMemberReader.findByChatRoomId(10L))
                .willReturn(List.of(membership(10L, ME, 5L), membership(10L, 2L, null)));
        given(profileService.getProfile(3L)).willReturn(profile("바다"));
        given(profileService.getProfile(2L)).willReturn(profile("하늘"));
        given(chatMessageReader.findLast(20L, Set.of())).willReturn(Optional.of(message(3L, 20L, 3L, "헤이", older)));
        given(chatMessageReader.findLast(10L, Set.of())).willReturn(Optional.of(message(8L, 10L, 2L, "안녕", newer)));
        given(chatMessageReader.countUnread(20L, null, Set.of(ME))).willReturn(1L);
        given(chatMessageReader.countUnread(10L, 5L, Set.of(ME))).willReturn(2L);

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
        given(chatRoomReader.findById(10L)).willReturn(Optional.of(directRoom(10L)));
        given(chatRoomMemberReader.findByChatRoomId(10L))
                .willReturn(List.of(membership(10L, ME, null), membership(10L, 2L, null)));
        // sorted_order가 뒤섞여 있어도 최소값(0)인 이미지가 대표로 선택된다
        given(profileService.getProfile(2L)).willReturn(profileWithImages("하늘",
                new ProfileResponse.ImageInfo(31L, "https://cdn/second.jpg", 2),
                new ProfileResponse.ImageInfo(30L, "https://cdn/rep.jpg", 0),
                new ProfileResponse.ImageInfo(32L, "https://cdn/third.jpg", 1)));
        given(chatMessageReader.findLast(10L, Set.of())).willReturn(Optional.empty());
        given(chatMessageReader.countUnread(10L, null, Set.of(ME))).willReturn(0L);

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
    @DisplayName("여러 GROUP 채팅 중 요청한 소개팅방의 채팅방 id만 반환한다")
    void givenMultipleGroupRooms_whenGetBySourceRoom_thenReturnsRequestedChatRoomId() {
        Long sourceRoomId = 101L;
        Long requestedChatRoomId = 20L;
        given(chatRoomReader.getByRoomId(sourceRoomId))
                .willReturn(groupRoom(requestedChatRoomId, sourceRoomId));
        given(chatRoomMemberReader.findMembership(requestedChatRoomId, ME))
                .willReturn(Optional.of(membership(requestedChatRoomId, ME, null)));

        GroupChatRoomResponse result = chatService.getGroupRoomBySourceRoom(sourceRoomId, ME);

        assertThat(result.chatRoomId()).isEqualTo(requestedChatRoomId);
        then(chatRoomReader).should().getByRoomId(sourceRoomId);
        then(chatRoomMemberReader).should().findMembership(requestedChatRoomId, ME);
    }

    @Test
    @DisplayName("소개팅방의 GROUP 채팅이 아직 없으면 ChatRoomNotFoundException(404)이 발생한다")
    void givenGroupRoomNotCreated_whenGetBySourceRoom_thenThrowsNotFound() {
        Long sourceRoomId = 101L;
        given(chatRoomReader.getByRoomId(sourceRoomId)).willThrow(new ChatRoomNotFoundException());

        assertThatThrownBy(() -> chatService.getGroupRoomBySourceRoom(sourceRoomId, ME))
                .isInstanceOf(ChatRoomNotFoundException.class);

        then(chatRoomMemberReader).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GROUP 채팅의 활성 멤버가 아니면 ChatNotMemberException(403)이 발생한다")
    void givenNotGroupChatMember_whenGetBySourceRoom_thenThrowsForbidden() {
        Long sourceRoomId = 101L;
        given(chatRoomReader.getByRoomId(sourceRoomId))
                .willReturn(groupRoom(ROOM, sourceRoomId));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getGroupRoomBySourceRoom(sourceRoomId, ME))
                .isInstanceOf(ChatNotMemberException.class);
    }

    @Test
    @DisplayName("GROUP 채팅에서 나간 멤버는 방 ID로 조회할 수 없다")
    void givenLeftGroupChatMember_whenGetBySourceRoom_thenThrowsForbidden() {
        Long sourceRoomId = 101L;
        ChatRoomMember left = membership(ROOM, ME, null).leave(LocalDateTime.now(clock));
        given(chatRoomReader.getByRoomId(sourceRoomId))
                .willReturn(groupRoom(ROOM, sourceRoomId));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(left));

        assertThatThrownBy(() -> chatService.getGroupRoomBySourceRoom(sourceRoomId, ME))
                .isInstanceOf(ChatNotMemberException.class);
    }

    @Test
    @DisplayName("GROUP 채팅 상세의 참여자 이름은 실명이 아니라 방 세션 가명(없으면 익명)으로 내려간다(#393)")
    void givenGroupRoom_whenGetRoom_thenShowsPseudonymNotRealName() {
        Long sourceRoomId = 77L;
        given(chatRoomReader.getById(ROOM)).willReturn(groupRoom(ROOM, sourceRoomId));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM)).willReturn(List.of(
                membership(ROOM, ME, null), membership(ROOM, 2L, null), membership(ROOM, 3L, null)));
        given(userService.isWithdrawn(2L)).willReturn(false);
        given(userService.isWithdrawn(3L)).willReturn(false);
        given(profileService.getProfile(2L)).willReturn(profile("실명둘"));
        given(profileService.getProfile(3L)).willReturn(profile("실명셋"));
        // 소개팅 방 멤버 배치 조회: 2L은 가명 있음, 3L은 가명 없음(성별 미상) → 익명
        given(roomMemberReader.getByRoomId(sourceRoomId)).willReturn(List.of(
                new org.son.haruharu.domain.room.implement.RoomMember(101L, sourceRoomId, 2L, "MALE", "민준", null, LocalDateTime.now(clock)),
                new org.son.haruharu.domain.room.implement.RoomMember(102L, sourceRoomId, 3L, null, null, null, LocalDateTime.now(clock))));

        ChatRoomDetailResponse result = chatService.getRoom(ROOM, ME);

        // 실명(실명둘·실명셋)은 노출되지 않고 가명/익명만 내려간다
        assertThat(result.members()).extracting(PartnerBrief::nickname)
                .containsExactlyInAnyOrder("민준", "익명");
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
        given(chatMessageReader.countUnread(ROOM, 7L, Set.of(ME))).willReturn(0L);

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
        given(chatRoomReader.findById(ROOM)).willReturn(Optional.of(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock))));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));

        chatService.leaveRoom(ROOM, ME);

        ArgumentCaptor<ChatRoomMember> captor = ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberWriter, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(m -> assertThat(m.leftAt()).isEqualTo(LocalDateTime.now(clock)));
        verify(chatRoomWriter).delete(ROOM);
        verify(eventPublisher).publishEvent(new org.son.haruharu.domain.chat.business.event.ChatRoomClosedEvent(ROOM, List.of(ME, 2L)));
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
    @DisplayName("GROUP 채팅 메시지 발신자명은 실명이 아니라 방 세션 가명으로 이벤트에 실린다(#393)")
    void givenGroupRoom_whenSendMessage_thenPublishesPseudonymAsSenderName() {
        Long chatRoomId = 30L;
        Long sourceRoomId = 77L;
        given(chatRoomReader.getById(chatRoomId)).willReturn(groupRoom(chatRoomId, sourceRoomId));
        given(chatRoomMemberReader.findMembership(chatRoomId, ME)).willReturn(Optional.of(membership(chatRoomId, ME, null)));
        given(chatMessageWriter.save(any()))
                .willReturn(new ChatMessage(50L, chatRoomId, ME, "안녕", LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findByChatRoomId(chatRoomId))
                .willReturn(List.of(membership(chatRoomId, ME, null), membership(chatRoomId, 2L, null)));
        // 발신자의 방 세션 가명
        given(roomMemberReader.findByRoomIdAndUserId(sourceRoomId, ME))
                .willReturn(Optional.of(new org.son.haruharu.domain.room.implement.RoomMember(
                        1L, sourceRoomId, ME, "MALE", "민준", null, LocalDateTime.now(clock))));

        chatService.sendMessage(chatRoomId, ME, "안녕");

        then(eventPublisher).should().publishEvent(new ChatMessageSentEvent(
                50L, chatRoomId, ChatRoomType.GROUP, sourceRoomId, ME,
                List.of(2L), "민준", LocalDateTime.now(clock)));
        // GROUP은 가명을 쓰므로 실명 조회(getNickname)를 하지 않는다
        then(profileService).should(never()).getNickname(any());
    }

    @Test
    @DisplayName("답장은 원문 보낸 사람 표시 이름과 앞 50자를 스냅샷으로 남기고 이벤트에 원문 주인을 싣는다(#456)")
    void givenReplyInSameRoom_whenSendMessage_thenSnapshotsAndPublishesReplyTarget() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        String longText = "가".repeat(60);
        given(chatMessageReader.findById(880L)).willReturn(Optional.of(message(880L, ROOM, 2L, longText, LocalDateTime.now(clock))));
        given(profileService.getNickname(2L)).willReturn("선우");
        given(profileService.getNickname(ME)).willReturn("윤슬");
        given(chatMessageWriter.save(any())).willAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            return new ChatMessage(900L, m.chatRoomId(), m.senderId(), m.content(), LocalDateTime.now(clock), null, null,
                    m.replyToMessageId(), m.replyPreviewSender(), m.replyPreviewContent());
        });

        ChatMessageBroadcast result = chatService.sendMessage(ROOM, ME, "ㅋㅋ", 880L);

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageWriter).save(saved.capture());
        assertThat(saved.getValue().replyToMessageId()).isEqualTo(880L);
        assertThat(saved.getValue().replyPreviewSender()).isEqualTo("선우");
        assertThat(saved.getValue().replyPreviewContent()).isEqualTo("가".repeat(50));
        assertThat(result.replyTo().available()).isTrue();
        assertThat(result.replyTo().senderName()).isEqualTo("선우");
        then(eventPublisher).should().publishEvent(new ChatMessageSentEvent(
                900L, ROOM, ChatRoomType.DIRECT, null, ME, List.of(2L), "윤슬", LocalDateTime.now(clock), 2L));
    }

    @Test
    @DisplayName("다른 채팅방 메시지나 없는 메시지에 답장하면 CHAT_REPLY_INVALID 이고 저장하지 않는다(#456)")
    void givenReplyToOtherRoom_whenSendMessage_thenThrowsInvalid() {
        given(chatRoomReader.getById(ROOM)).willReturn(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock)));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(chatMessageReader.findById(880L)).willReturn(Optional.of(message(880L, 77L, 2L, "x", LocalDateTime.now(clock))));
        given(chatMessageReader.findById(881L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(ROOM, ME, "ㅋㅋ", 880L))
                .isInstanceOf(org.son.haruharu.domain.chat.business.exception.ChatReplyInvalidException.class);
        assertThatThrownBy(() -> chatService.sendMessage(ROOM, ME, "ㅋㅋ", 881L))
                .isInstanceOf(org.son.haruharu.domain.chat.business.exception.ChatReplyInvalidException.class);
        then(chatMessageWriter).should(never()).save(any());
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
        given(chatRoomReader.findById(ROOM)).willReturn(Optional.of(directRoom(ROOM)));
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
        given(chatRoomReader.findById(20L)).willReturn(Optional.of(directRoom(20L)));
        given(chatRoomReader.findById(10L)).willReturn(Optional.of(directRoom(10L)));
        given(chatRoomMemberReader.findByChatRoomId(20L))
                .willReturn(List.of(membership(20L, ME, null), membership(20L, 3L, null)));
        given(chatRoomMemberReader.findByChatRoomId(10L))
                .willReturn(List.of(membership(10L, ME, null), membership(10L, 2L, null)));
        given(userService.isWithdrawn(3L)).willReturn(true);
        given(userService.isWithdrawn(2L)).willReturn(false);
        given(profileService.getProfile(2L)).willReturn(profile("하늘"));
        given(chatMessageReader.findLast(10L, Set.of()))
                .willReturn(Optional.of(message(8L, 10L, 2L, "안녕", LocalDateTime.of(2026, 7, 3, 11, 0))));
        given(chatMessageReader.countUnread(10L, null, Set.of(ME))).willReturn(2L);

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

    // ── 차단(#408) — 1:1 양방향 제한 ──────────────────────────

    @Test
    @DisplayName("차단 관계인 DIRECT 방의 메시지는 조회할 수 없다(어느 쪽이 차단했든 403)")
    void givenBlockedPartner_whenGetMessages_thenThrowsForbidden() {
        given(chatRoomReader.getById(ROOM)).willReturn(directRoom(ROOM));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(blockReader.isBlockedBetween(ME, 2L)).willReturn(true);

        assertThatThrownBy(() -> chatService.getMessages(ROOM, ME, null, 30))
                .isInstanceOf(ChatPartnerBlockedException.class);
    }

    @Test
    @DisplayName("차단 관계인 DIRECT 방에는 메시지를 전송할 수 없다(STOMP 직접 발행 경로 포함)")
    void givenBlockedPartner_whenSendMessage_thenThrowsAndDoesNotSaveOrPublish() {
        given(chatRoomReader.getById(ROOM)).willReturn(directRoom(ROOM));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(blockReader.isBlockedBetween(ME, 2L)).willReturn(true);

        assertThatThrownBy(() -> chatService.sendMessage(ROOM, ME, "안녕"))
                .isInstanceOf(ChatPartnerBlockedException.class);
        then(chatMessageWriter).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("차단 관계인 DIRECT 방은 읽음 처리를 할 수 없다")
    void givenBlockedPartner_whenMarkRead_thenThrowsForbidden() {
        given(chatRoomReader.getById(ROOM)).willReturn(directRoom(ROOM));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(blockReader.isBlockedBetween(ME, 2L)).willReturn(true);

        assertThatThrownBy(() -> chatService.markRead(ROOM, ME, 7L))
                .isInstanceOf(ChatPartnerBlockedException.class);
        then(chatRoomMemberWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("차단 관계인 DIRECT 방은 채팅방 목록에서 제외된다(기록은 보존)")
    void givenBlockedPartner_whenGetMyRooms_thenRoomExcluded() {
        given(chatRoomMemberReader.findActiveByUserId(ME))
                .willReturn(List.of(membership(ROOM, ME, null)));
        given(chatRoomReader.findById(ROOM)).willReturn(Optional.of(directRoom(ROOM)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));
        given(blockReader.getRelatedUserIds(ME)).willReturn(Set.of(2L));

        ChatRoomsResponse result = chatService.getMyRooms(ME);

        assertThat(result.chatRooms()).isEmpty();
        // 차단 방은 프로필·메시지 조회를 아예 건드리지 않는다
        then(profileService).should(never()).getProfile(any());
    }

    // ── 차단(#408) — 그룹채팅 미리보기·안읽음 (내가 차단한 상대 기준 단방향) ──

    @Test
    @DisplayName("그룹채팅 목록에서 내가 차단한 상대의 메시지는 미리보기·안읽음 수에서 제외된다")
    void givenBlockedSender_whenGetMyRooms_thenPreviewAndUnreadExcludeBlocked() {
        LocalDateTime at = LocalDateTime.of(2026, 9, 9, 11, 0);
        given(chatRoomMemberReader.findActiveByUserId(ME))
                .willReturn(List.of(membership(10L, ME, null)));
        given(chatRoomReader.findById(10L)).willReturn(Optional.of(groupRoom(10L, ROOM)));
        given(chatRoomMemberReader.findByChatRoomId(10L))
                .willReturn(List.of(membership(10L, ME, null), membership(10L, 2L, null), membership(10L, 3L, null)));
        given(roomMemberReader.getByRoomId(ROOM)).willReturn(List.of());
        given(profileService.getProfile(2L)).willReturn(profile("하늘"));
        given(profileService.getProfile(3L)).willReturn(profile("바다"));
        given(blockReader.getBlockedUserIds(ME)).willReturn(Set.of(2L));
        // 차단 발신자(2L)를 제외한 마지막 메시지·안읽음 수가 조회된다
        given(chatMessageReader.findLast(10L, Set.of(2L))).willReturn(Optional.of(message(8L, 10L, 3L, "안녕", at)));
        given(chatMessageReader.countUnread(10L, null, Set.of(ME, 2L))).willReturn(1L);

        ChatRoomsResponse result = chatService.getMyRooms(ME);

        ChatRoomsResponse.ChatRoomSummary summary = result.chatRooms().get(0);
        assertThat(summary.lastMessage()).isEqualTo("안녕");
        assertThat(summary.unreadCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("그룹채팅 읽음 처리의 안읽음 수에서도 내가 차단한 상대의 메시지가 제외된다")
    void givenBlockedSender_whenMarkRead_thenUnreadExcludesBlocked() {
        given(chatRoomReader.getById(10L)).willReturn(groupRoom(10L, ROOM));
        given(chatRoomMemberReader.findMembership(10L, ME)).willReturn(Optional.of(membership(10L, ME, 3L)));
        given(chatRoomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(blockReader.getBlockedUserIds(ME)).willReturn(Set.of(2L));
        given(chatMessageReader.countUnread(10L, 7L, Set.of(ME, 2L))).willReturn(0L);

        ReadResponse result = chatService.markRead(10L, ME, 7L);

        assertThat(result.unreadCount()).isZero();
    }

    @Test
    @DisplayName("상대가 탈퇴한 방도 나가기는 가능하다 — 목록에서 치울 수단이 필요하다")
    void givenWithdrawnPartner_whenLeaveRoom_thenSucceeds() {
        given(chatRoomReader.findById(ROOM)).willReturn(Optional.of(new ChatRoom(ROOM, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock))));
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomMemberReader.findByChatRoomId(ROOM))
                .willReturn(List.of(membership(ROOM, ME, null), membership(ROOM, 2L, null)));

        chatService.leaveRoom(ROOM, ME);

        ArgumentCaptor<ChatRoomMember> captor = ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberWriter, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(m -> assertThat(m.leftAt()).isEqualTo(LocalDateTime.now(clock)));
        verify(chatRoomWriter).delete(ROOM);
        verify(eventPublisher).publishEvent(new org.son.haruharu.domain.chat.business.event.ChatRoomClosedEvent(ROOM, List.of(ME, 2L)));
    }

    @Test
    @DisplayName("상호매칭 성사 시 DIRECT 채팅방과 두 참여자를 생성한다")
    void createRoomFromMatch_createsRoomAndTwoMembers() {
        given(chatRoomReader.existsIncludingDeletedByMatchResultId(500L)).willReturn(false);
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
        given(chatRoomReader.existsIncludingDeletedByMatchResultId(500L)).willReturn(true);

        chatService.createRoomFromMatch(500L, 1L, 2L);

        then(chatRoomWriter).should(never()).save(any());
        then(chatRoomMemberWriter).should(never()).save(any());
    }

    // ── GROUP 채팅 ──────────────────────────────────────────

    private Room sourceRoom() {
        LocalDateTime start = LocalDateTime.now(clock);
        return Room.createScheduled(start, start.plusDays(2), start.plusDays(2).plusHours(1),
                start.plusDays(2).plusHours(2), start.plusDays(3), 3);
    }

    @Test
    @DisplayName("그룹채팅이 있으면 입장 시각으로 새 멤버를 등록한다")
    void givenGroupRoom_whenAddMember_thenSavesMembership() {
        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(sourceRoom()));
        given(chatRoomReader.findByRoomId(ROOM)).willReturn(Optional.of(groupRoom(88L, ROOM)));
        LocalDateTime joinedAt = LocalDateTime.now(clock);

        chatService.addGroupRoomMember(ROOM, ME, joinedAt);

        then(chatRoomMemberWriter).should().save(ChatRoomMember.join(88L, ME, joinedAt));
    }

    @Test
    @DisplayName("그룹채팅이 아직 없으면 멤버 등록은 방 생성 시점으로 미룬다")
    void givenNoGroupRoom_whenAddMember_thenSkips() {
        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(sourceRoom()));

        chatService.addGroupRoomMember(ROOM, ME, LocalDateTime.now(clock));

        then(chatRoomMemberWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 등록된 채팅 멤버는 읽음 커서와 퇴장 상태를 유지한다")
    void givenExistingMembership_whenAddMemberAgain_thenPreservesMembership() {
        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(sourceRoom()));
        given(chatRoomReader.findByRoomId(ROOM)).willReturn(Optional.of(groupRoom(88L, ROOM)));
        given(chatRoomMemberReader.findMembership(88L, ME)).willReturn(Optional.of(
                membership(88L, ME, 99L).leave(LocalDateTime.now(clock))));

        chatService.addGroupRoomMember(ROOM, ME, LocalDateTime.now(clock));

        then(chatRoomMemberWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소개팅방이 없으면 지연된 그룹채팅 생성과 멤버 등록을 무시한다")
    void givenMissingSourceRoom_whenCreateOrAdd_thenSkips() {
        chatService.createGroupRoom(ROOM);
        chatService.addGroupRoomMember(ROOM, ME, LocalDateTime.now(clock));

        then(chatRoomWriter).shouldHaveNoInteractions();
        then(chatRoomMemberWriter).shouldHaveNoInteractions();
    }


    @Test
    @DisplayName("방 시작 시 GROUP 채팅방과 멤버 전원(6명 스냅샷)을 생성한다")
    void createGroupRoom_createsGroupRoomAndAllMembers() {
        given(chatRoomReader.existsByRoomId(ROOM)).willReturn(false);
        given(chatRoomWriter.save(any())).willReturn(groupRoom(88L, ROOM));

        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(sourceRoom()));
        given(roomMemberReader.getByRoomId(ROOM)).willReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L).stream()
                .map(id -> org.son.haruharu.domain.room.implement.RoomMember.of(ROOM, id, null, null, LocalDateTime.now(clock)))
                .toList());
        chatService.createGroupRoom(ROOM);

        var order = org.mockito.Mockito.inOrder(roomReader, roomMemberReader);
        order.verify(roomReader).findByIdForUpdate(ROOM);
        order.verify(roomMemberReader).getByRoomId(ROOM);
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

        given(roomReader.findByIdForUpdate(ROOM)).willReturn(Optional.of(sourceRoom()));
        chatService.createGroupRoom(ROOM);

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

        chatService.sendMessage(ROOM, ME, "안녕");

        ArgumentCaptor<ChatMessageSentEvent> captor = ArgumentCaptor.forClass(ChatMessageSentEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().recipientUserIds()).containsExactly(2L); // 나간 3L 제외
    }

    @Test
    @DisplayName("이미 종료된 방의 전 참여자 재시도는 변경이나 이벤트 없이 성공한다")
    void givenClosedRoom_whenFormerMemberRetries_thenNoChanges() {
        given(chatRoomMemberReader.findMembership(ROOM, ME))
                .willReturn(Optional.of(membership(ROOM, ME, null).leave(LocalDateTime.now(clock))));
        given(chatRoomReader.findById(ROOM)).willReturn(Optional.empty());
        chatService.leaveRoom(ROOM, ME);
        then(chatRoomWriter).shouldHaveNoInteractions();
        then(chatRoomMemberWriter).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("비참여자는 방 종료를 요청할 수 없다")
    void givenOutsider_whenLeave_thenForbidden() {
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.empty());
        assertThatThrownBy(() -> chatService.leaveRoom(ROOM, ME)).isInstanceOf(ChatNotMemberException.class);
        then(chatRoomWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("참여 상태만 남은 삭제된 방은 없는 방으로 응답한다")
    void givenDeletedRoomWithActiveMembership_whenLeave_thenNotFound() {
        given(chatRoomMemberReader.findMembership(ROOM, ME)).willReturn(Optional.of(membership(ROOM, ME, null)));
        given(chatRoomReader.findById(ROOM)).willReturn(Optional.empty());
        assertThatThrownBy(() -> chatService.leaveRoom(ROOM, ME)).isInstanceOf(org.son.haruharu.domain.chat.storage.exception.ChatRoomNotFoundException.class);
    }

    @Test
    @DisplayName("GROUP에서 이미 나간 참여자의 재시도는 기존대로 거절한다")
    void givenLeftGroupMember_whenLeave_thenForbidden() {
        given(chatRoomMemberReader.findMembership(ROOM, ME))
                .willReturn(Optional.of(membership(ROOM, ME, null).leave(LocalDateTime.now(clock))));
        given(chatRoomReader.findById(ROOM)).willReturn(Optional.of(groupRoom(ROOM, 123L)));
        assertThatThrownBy(() -> chatService.leaveRoom(ROOM, ME)).isInstanceOf(ChatNotMemberException.class);
        then(chatRoomWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("목록 조회 중 종료된 방은 목록 전체 오류 대신 제외한다")
    void givenRoomClosedDuringList_whenList_thenSkips() {
        given(chatRoomMemberReader.findActiveByUserId(ME)).willReturn(List.of(membership(ROOM, ME, null)));
        given(chatRoomReader.findById(ROOM)).willReturn(Optional.empty());
        assertThat(chatService.getMyRooms(ME).chatRooms()).isEmpty();
    }

}
