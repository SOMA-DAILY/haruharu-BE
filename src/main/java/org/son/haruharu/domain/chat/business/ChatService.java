package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.dto.ChatRoomDetailResponse;
import org.son.haruharu.domain.chat.business.dto.ChatRoomsResponse;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse;
import org.son.haruharu.domain.chat.business.dto.PartnerBrief;
import org.son.haruharu.domain.chat.business.dto.ReadResponse;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatMessageWriter;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberWriter;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.ChatRoomWriter;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 채팅 유스케이스(조회·상태 변경). 실시간 메시지 송수신은 #60(STOMP)에서 담당한다.
 * 상대 프로필은 profile 도메인을 서비스 레벨에서 조율해 조회한다.
 */
@Service
@Transactional(readOnly = true)
public class ChatService {

    private static final int MAX_MESSAGE_PAGE_SIZE = 100;

    private final ChatRoomReader chatRoomReader;
    private final ChatRoomWriter chatRoomWriter;
    private final ChatRoomMemberReader chatRoomMemberReader;
    private final ChatRoomMemberWriter chatRoomMemberWriter;
    private final ChatMessageReader chatMessageReader;
    private final ChatMessageWriter chatMessageWriter;
    private final ProfileService profileService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ChatService(ChatRoomReader chatRoomReader,
                       ChatRoomWriter chatRoomWriter,
                       ChatRoomMemberReader chatRoomMemberReader,
                       ChatRoomMemberWriter chatRoomMemberWriter,
                       ChatMessageReader chatMessageReader,
                       ChatMessageWriter chatMessageWriter,
                       ProfileService profileService,
                       ApplicationEventPublisher eventPublisher,
                       Clock clock) {
        this.chatRoomReader = chatRoomReader;
        this.chatRoomWriter = chatRoomWriter;
        this.chatRoomMemberReader = chatRoomMemberReader;
        this.chatRoomMemberWriter = chatRoomMemberWriter;
        this.chatMessageReader = chatMessageReader;
        this.chatMessageWriter = chatMessageWriter;
        this.profileService = profileService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 상호매칭 성사 시 두 유저의 1:1 채팅방을 생성한다(#61, MutualMatchEvent 커밋 후 호출).
     * match_result_id 당 1방 멱등 — 이벤트 재발행·재시도에도 중복 생성하지 않는다(unique + 사전 검사).
     *
     * AFTER_COMMIT 리스너에서 호출되므로 REQUIRES_NEW로 새 트랜잭션을 연다(#158).
     * 원본(최종선택) 트랜잭션은 이미 커밋된 상태라, REQUIRED면 커밋된 트랜잭션에 join되어 INSERT가 유실된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createRoomFromMatch(Long matchResultId, Long userIdA, Long userIdB) {
        if (chatRoomReader.existsByMatchResultId(matchResultId)) {
            return;
        }
        ChatRoom room = chatRoomWriter.save(ChatRoom.create(matchResultId));
        LocalDateTime now = LocalDateTime.now(clock);
        chatRoomMemberWriter.save(ChatRoomMember.join(room.id(), userIdA, now));
        chatRoomMemberWriter.save(ChatRoomMember.join(room.id(), userIdB, now));
    }

    /**
     * 메시지 전송(#60 STOMP). 발신자가 방 참여자인지 검증 후 저장하고, 브로드캐스트 payload를 반환한다.
     * 실제 브로드캐스트(convertAndSend)는 application 계층(ChatStompController)이 담당한다.
     */
    @Transactional
    public ChatMessageBroadcast sendMessage(Long chatRoomId, Long senderId, String content) {
        requireMember(chatRoomId, senderId);
        ChatMessage saved = chatMessageWriter.save(ChatMessage.create(chatRoomId, senderId, content));
        Long recipientId = partnerUserId(chatRoomId, senderId);
        String senderNickname = profileService.getNickname(senderId);
        eventPublisher.publishEvent(new ChatMessageSentEvent(chatRoomId, recipientId, senderNickname));
        return ChatMessageBroadcast.from(saved);
    }

    /** 내 채팅방 목록. 마지막 메시지 최신순으로 정렬한다. */
    public ChatRoomsResponse getMyRooms(Long userId) {
        List<ChatRoomsResponse.ChatRoomSummary> summaries = chatRoomMemberReader.findActiveByUserId(userId).stream()
                .map(me -> toSummary(me, userId))
                .sorted(Comparator.comparing(
                        ChatRoomsResponse.ChatRoomSummary::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new ChatRoomsResponse(summaries);
    }

    public ChatRoomDetailResponse getRoom(Long chatRoomId, Long userId) {
        requireMember(chatRoomId, userId);
        ChatRoom room = chatRoomReader.getById(chatRoomId);
        return new ChatRoomDetailResponse(chatRoomId, partnerBrief(chatRoomId, userId), room.createdAt());
    }

    /** 과거 메시지(최신순 커서 페이지). 삭제된 메시지는 제외. */
    public MessagePageResponse getMessages(Long chatRoomId, Long userId, Long cursor, int size) {
        requireMember(chatRoomId, userId);
        int limit = Math.min(Math.max(size, 1), MAX_MESSAGE_PAGE_SIZE);
        List<ChatMessage> messages = chatMessageReader.findPage(chatRoomId, cursor, limit);
        return MessagePageResponse.of(messages, limit);
    }

    /** 읽음 커서 갱신. 커서는 뒤로만 이동한다(역행 방지). */
    @Transactional
    public ReadResponse markRead(Long chatRoomId, Long userId, Long lastReadMessageId) {
        ChatRoomMember member = requireMember(chatRoomId, userId);
        ChatRoomMember updated = chatRoomMemberWriter.save(member.readUpTo(lastReadMessageId));
        long unread = chatMessageReader.countUnread(chatRoomId, updated.lastReadMessageId(), userId);
        return new ReadResponse(chatRoomId, unread);
    }

    /** 채팅방 나가기. leftAt을 기록한다(방·메시지 데이터는 보존). */
    @Transactional
    public void leaveRoom(Long chatRoomId, Long userId) {
        ChatRoomMember member = requireMember(chatRoomId, userId);
        chatRoomMemberWriter.save(member.leave(LocalDateTime.now(clock)));
    }

    private ChatRoomsResponse.ChatRoomSummary toSummary(ChatRoomMember me, Long userId) {
        Long partnerUserId = partnerUserId(me.chatRoomId(), userId);
        String nickname = profileService.getProfile(partnerUserId).nickname();
        Optional<ChatMessage> last = chatMessageReader.findLast(me.chatRoomId());
        long unread = chatMessageReader.countUnread(me.chatRoomId(), me.lastReadMessageId(), userId);
        return new ChatRoomsResponse.ChatRoomSummary(
                me.chatRoomId(),
                new PartnerBrief(partnerUserId, nickname),
                last.map(ChatMessage::content).orElse(null),
                last.map(ChatMessage::createdAt).orElse(null),
                unread);
    }

    private PartnerBrief partnerBrief(Long chatRoomId, Long userId) {
        Long partnerUserId = partnerUserId(chatRoomId, userId);
        return new PartnerBrief(partnerUserId, profileService.getProfile(partnerUserId).nickname());
    }

    /** 방 존재(404) 확인 후 요청자가 참여자(403)인지 검증하고 멤버십을 반환한다. */
    private ChatRoomMember requireMember(Long chatRoomId, Long userId) {
        chatRoomReader.getById(chatRoomId);
        return chatRoomMemberReader.findMembership(chatRoomId, userId)
                .filter(member -> !member.hasLeft())
                .orElseThrow(ChatNotMemberException::new);
    }

    private Long partnerUserId(Long chatRoomId, Long userId) {
        return chatRoomMemberReader.findByChatRoomId(chatRoomId).stream()
                .map(ChatRoomMember::userId)
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElseThrow(ChatNotMemberException::new);
    }
}
