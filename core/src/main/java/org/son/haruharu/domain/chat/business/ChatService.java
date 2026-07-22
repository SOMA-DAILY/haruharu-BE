package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.dto.ChatRoomDetailResponse;
import org.son.haruharu.domain.chat.business.dto.ChatRoomsResponse;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse;
import org.son.haruharu.domain.chat.business.dto.PartnerBrief;
import org.son.haruharu.domain.chat.business.dto.ReadResponse;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerWithdrawnException;
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
import org.son.haruharu.domain.user.business.UserService;
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
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ChatService(ChatRoomReader chatRoomReader,
                       ChatRoomWriter chatRoomWriter,
                       ChatRoomMemberReader chatRoomMemberReader,
                       ChatRoomMemberWriter chatRoomMemberWriter,
                       ChatMessageReader chatMessageReader,
                       ChatMessageWriter chatMessageWriter,
                       ProfileService profileService,
                       UserService userService,
                       ApplicationEventPublisher eventPublisher,
                       Clock clock) {
        this.chatRoomReader = chatRoomReader;
        this.chatRoomWriter = chatRoomWriter;
        this.chatRoomMemberReader = chatRoomMemberReader;
        this.chatRoomMemberWriter = chatRoomMemberWriter;
        this.chatMessageReader = chatMessageReader;
        this.chatMessageWriter = chatMessageWriter;
        this.profileService = profileService;
        this.userService = userService;
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
     * 데모 채팅 시드(#266). 리뷰어가 "미리 채워진 대화"를 보고 곧바로 답장까지 해볼 수 있도록,
     * 실제 chat 테이블에 방·멤버·대화를 만든다(별도 데모 테이블 없음 → FE가 기존 채팅 화면 그대로 사용).
     * <p>
     * 기존 {@code createRoomFromMatch}는 AFTER_COMMIT 리스너용이라 REQUIRES_NEW로 새 트랜잭션을 여는데,
     * 데모는 같은 트랜잭션에서 방금 만든 매칭 결과를 참조하므로 REQUIRED(기본)로 동작하는 별도 경로가 필요하다.
     * 이미 방이 있으면 대화만 다시 채운다(멱등).
     *
     * @return 생성(또는 기존) 채팅방 id
     */
    @Transactional
    public Long seedDemoChat(Long matchResultId, Long reviewerUserId, Long partnerUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Long chatRoomId = chatRoomReader.findChatRoomIdByMatchResultId(matchResultId)
                .orElseGet(() -> {
                    ChatRoom room = chatRoomWriter.save(ChatRoom.create(matchResultId));
                    chatRoomMemberWriter.save(ChatRoomMember.join(room.id(), reviewerUserId, now));
                    chatRoomMemberWriter.save(ChatRoomMember.join(room.id(), partnerUserId, now));
                    return room.id();
                });

        // 재시드 시 대화가 누적되지 않도록 기존 메시지를 물리 삭제하고 다시 채운다.
        chatMessageWriter.hardDeleteByChatRoomId(chatRoomId);
        for (DemoChatScript.Line line : DemoChatScript.LINES) {
            Long senderId = line.fromPartner() ? partnerUserId : reviewerUserId;
            chatMessageWriter.save(ChatMessage.create(chatRoomId, senderId, line.content()));
        }
        return chatRoomId;
    }

    /**
     * 데모 채팅 정리(#266). 메시지 → 멤버 → 방 순서로 물리 삭제한다(FK 역순).
     * 전역 soft-delete + UNIQUE(match_result_id) 때문에 soft 삭제로는 재시연 시 방 재생성이 막혀 하드삭제한다.
     */
    @Transactional
    public void purgeDemoChat(Long matchResultId) {
        chatRoomReader.findChatRoomIdByMatchResultId(matchResultId).ifPresent(chatRoomId -> {
            chatMessageWriter.hardDeleteByChatRoomId(chatRoomId);
            chatRoomMemberWriter.deleteByChatRoomId(chatRoomId);
        });
        chatRoomWriter.hardDeleteByMatchResultId(matchResultId);
    }

    /**
     * 메시지 전송(#60 STOMP). 발신자가 방 참여자인지 검증 후 저장하고, 브로드캐스트 payload를 반환한다.
     * 실제 브로드캐스트(convertAndSend)는 application 계층(ChatStompController)이 담당한다.
     */
    @Transactional
    public ChatMessageBroadcast sendMessage(Long chatRoomId, Long senderId, String content) {
        requireMember(chatRoomId, senderId);
        Long recipientId = requireActivePartner(chatRoomId, senderId);
        ChatMessage saved = chatMessageWriter.save(ChatMessage.create(chatRoomId, senderId, content));
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
        Long partnerUserId = requireActivePartner(chatRoomId, userId);
        ChatRoom room = chatRoomReader.getById(chatRoomId);
        return new ChatRoomDetailResponse(chatRoomId, toPartnerBrief(partnerUserId), room.createdAt());
    }

    /** 과거 메시지(최신순 커서 페이지). 삭제된 메시지는 제외. */
    public MessagePageResponse getMessages(Long chatRoomId, Long userId, Long cursor, int size) {
        requireMember(chatRoomId, userId);
        requireActivePartner(chatRoomId, userId);
        int limit = Math.min(Math.max(size, 1), MAX_MESSAGE_PAGE_SIZE);
        List<ChatMessage> messages = chatMessageReader.findPage(chatRoomId, cursor, limit);
        return MessagePageResponse.of(messages, limit);
    }

    /** 읽음 커서 갱신. 커서는 뒤로만 이동한다(역행 방지). */
    @Transactional
    public ReadResponse markRead(Long chatRoomId, Long userId, Long lastReadMessageId) {
        ChatRoomMember member = requireMember(chatRoomId, userId);
        requireActivePartner(chatRoomId, userId);
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
        // 탈퇴자는 프로필이 soft-delete되어 조회 시 404가 난다. 조회 전에 걸러야 목록 전체가 죽지 않는다(#255).
        if (userService.isWithdrawn(partnerUserId)) {
            return ChatRoomsResponse.ChatRoomSummary.withdrawn(me.chatRoomId());
        }
        PartnerBrief partner = toPartnerBrief(partnerUserId);
        Optional<ChatMessage> last = chatMessageReader.findLast(me.chatRoomId());
        long unread = chatMessageReader.countUnread(me.chatRoomId(), me.lastReadMessageId(), userId);
        return new ChatRoomsResponse.ChatRoomSummary(
                me.chatRoomId(),
                partner,
                last.map(ChatMessage::content).orElse(null),
                last.map(ChatMessage::createdAt).orElse(null),
                unread,
                false);
    }

    /** 상대 프로필에서 목록·헤더용 경량 정보를 뽑는다. 대표사진 = sorted_order 최소 이미지(없으면 null). */
    private PartnerBrief toPartnerBrief(Long partnerUserId) {
        var profile = profileService.getProfile(partnerUserId);
        String profileImageUrl = profile.images().stream()
                .min(Comparator.comparingInt(img -> img.sortedOrder()))
                .map(img -> img.imageUrl())
                .orElse(null);
        return new PartnerBrief(partnerUserId, profile.nickname(), profileImageUrl);
    }

    /** 방 존재(404) 확인 후 요청자가 참여자(403)인지 검증하고 멤버십을 반환한다. */
    private ChatRoomMember requireMember(Long chatRoomId, Long userId) {
        chatRoomReader.getById(chatRoomId);
        return chatRoomMemberReader.findMembership(chatRoomId, userId)
                .filter(member -> !member.hasLeft())
                .orElseThrow(ChatNotMemberException::new);
    }

    /**
     * 상대가 탈퇴하지 않았는지 검증하고 상대 userId를 반환한다(#255).
     * 상대가 탈퇴한 방은 대화 기록을 볼 수 없고 진입할 수 없다 — 나가기(leaveRoom)만 허용된다.
     * 반환값을 호출부가 재사용해 상대 조회가 요청당 한 번만 일어나게 한다.
     */
    private Long requireActivePartner(Long chatRoomId, Long userId) {
        Long partnerUserId = partnerUserId(chatRoomId, userId);
        if (userService.isWithdrawn(partnerUserId)) {
            throw new ChatPartnerWithdrawnException();
        }
        return partnerUserId;
    }

    private Long partnerUserId(Long chatRoomId, Long userId) {
        return chatRoomMemberReader.findByChatRoomId(chatRoomId).stream()
                .map(ChatRoomMember::userId)
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElseThrow(ChatNotMemberException::new);
    }
}
