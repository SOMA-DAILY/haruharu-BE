package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.dto.ChatRoomDetailResponse;
import org.son.haruharu.domain.chat.business.dto.ChatRoomsResponse;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse;
import org.son.haruharu.domain.chat.business.dto.PartnerBrief;
import org.son.haruharu.domain.chat.business.dto.ReadResponse;
import org.son.haruharu.domain.chat.business.dto.UnreadCountResponse;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerWithdrawnException;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatMessageWriter;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberWriter;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.ChatRoomWriter;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.user.business.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
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
                       RoomReader roomReader,
                       RoomMemberReader roomMemberReader,
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
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
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
        ChatRoom room = chatRoomWriter.save(ChatRoom.createDirect(matchResultId));
        LocalDateTime now = LocalDateTime.now(clock);
        chatRoomMemberWriter.save(ChatRoomMember.join(room.id(), userIdA, now));
        chatRoomMemberWriter.save(ChatRoomMember.join(room.id(), userIdB, now));
    }

    /** 방 시작·멤버 입장 이벤트가 공유하는 GROUP 채팅 멱등 동기화. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void synchronizeGroupRoom(Long roomId) {
        Room sourceRoom = roomReader.findByIdForUpdate(roomId)
                .orElseThrow(RoomNotFoundException::new);
        RoomStatus status = sourceRoom.statusAt(LocalDateTime.now(clock));
        // 아직 시작 전(PENDING — 멤버 입장 이벤트는 방 시작 전에도 발화)·취소(CANCELLED) 방은 그룹채팅을 만들지 않는다.
        // 단 배치 장기 중단으로 PENDING→ENDED 직행한 방은 RoomStatusTransitionService가 복구용 RoomStartedEvent를
        // 한 번 발행하므로 ENDED 도 허용해야 한다(안 그러면 그 방 그룹채팅이 영구 누락되고 리컨사일러는 재발행하지 않는다).
        if (status == RoomStatus.PENDING || status == RoomStatus.CANCELLED) {
            return;
        }

        ChatRoom groupRoom = chatRoomReader.findByRoomIdForUpdate(roomId)
                .orElseGet(() -> chatRoomWriter.save(ChatRoom.createGroup(roomId)));
        List<ChatRoomMember> existingMembers =
                chatRoomMemberReader.findByChatRoomIdForUpdate(groupRoom.id());
        Set<Long> existingUserIds = new HashSet<>();
        existingMembers.stream().map(ChatRoomMember::userId).forEach(existingUserIds::add);

        for (RoomMember sourceMember : roomMemberReader.findByRoomIdOrdered(roomId)) {
            if (existingUserIds.add(sourceMember.userId())) {
                chatRoomMemberWriter.save(ChatRoomMember.join(
                        groupRoom.id(), sourceMember.userId(), sourceMember.joinedAt()));
            }
        }
    }

    /**
     * 메시지 전송(#60 STOMP). 발신자가 방 참여자인지 검증 후 저장하고, 브로드캐스트 payload를 반환한다.
     * 실제 브로드캐스트(convertAndSend)는 application 계층(ChatStompController)이 담당한다.
     */
    @Transactional
    public ChatMessageBroadcast sendMessage(Long chatRoomId, Long senderId, String content) {
        requireMember(chatRoomId, senderId);
        ChatRoom chatRoom = chatRoomReader.getById(chatRoomId);
        List<Long> recipientIds = recipientsFor(chatRoom, senderId); // DIRECT=상대 1명, GROUP=나 제외 전원
        ChatMessage saved = chatMessageWriter.save(ChatMessage.create(chatRoomId, senderId, content));
        String senderNickname = profileService.getNickname(senderId);
        eventPublisher.publishEvent(new ChatMessageSentEvent(
                saved.id(), chatRoomId, chatRoom.type(), chatRoom.roomId(), senderId,
                recipientIds, senderNickname, saved.createdAt()));
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
        if (room.type() == ChatRoomType.GROUP) {
            return ChatRoomDetailResponse.group(chatRoomId, otherMemberBriefs(chatRoomId, userId), room.createdAt());
        }
        Long partnerUserId = requireActivePartner(chatRoomId, userId);
        return ChatRoomDetailResponse.direct(chatRoomId, toPartnerBrief(partnerUserId), room.createdAt());
    }

    /** 과거 메시지(최신순 커서 페이지). 삭제된 메시지는 제외. */
    public MessagePageResponse getMessages(Long chatRoomId, Long userId, Long cursor, int size) {
        ChatRoomMember member = requireMember(chatRoomId, userId);
        requireActiveIfDirect(chatRoomId, userId);
        int limit = Math.min(Math.max(size, 1), MAX_MESSAGE_PAGE_SIZE);
        List<ChatMessage> messages = chatMessageReader.findPage(chatRoomId, member.joinedAt(), cursor, limit);
        return MessagePageResponse.of(messages, limit);
    }

    /** 읽음 커서 갱신. 커서는 뒤로만 이동한다(역행 방지). */
    @Transactional
    public ReadResponse markRead(Long chatRoomId, Long userId, Long lastReadMessageId) {
        ChatRoomMember member = requireMember(chatRoomId, userId);
        requireActiveIfDirect(chatRoomId, userId);
        ChatRoomMember updated = chatRoomMemberWriter.save(member.readUpTo(lastReadMessageId));
        long unread = chatMessageReader.countUnread(
                chatRoomId, updated.lastReadMessageId(), userId, updated.joinedAt());
        return new ReadResponse(chatRoomId, unread);
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(chatMessageReader.countUnreadByUserId(userId));
    }

    /** 채팅방 나가기. leftAt을 기록한다(방·메시지 데이터는 보존). */
    @Transactional
    public void leaveRoom(Long chatRoomId, Long userId) {
        ChatRoomMember member = requireMember(chatRoomId, userId);
        chatRoomMemberWriter.save(member.leave(LocalDateTime.now(clock)));
    }

    private ChatRoomsResponse.ChatRoomSummary toSummary(ChatRoomMember me, Long userId) {
        Long chatRoomId = me.chatRoomId();
        ChatRoom room = chatRoomReader.getById(chatRoomId);

        // DIRECT 상대 탈퇴는 마지막 메시지 조회 전에 걸러야 목록 전체가 죽지 않는다(#255).
        Long directPartnerUserId = null;
        if (room.type() == ChatRoomType.DIRECT) {
            directPartnerUserId = partnerUserId(chatRoomId, userId);
            if (userService.isWithdrawn(directPartnerUserId)) {
                return ChatRoomsResponse.ChatRoomSummary.withdrawn(chatRoomId);
            }
        }

        Optional<ChatMessage> last = chatMessageReader.findLast(chatRoomId, me.joinedAt());
        long unread = chatMessageReader.countUnread(
                chatRoomId, me.lastReadMessageId(), userId, me.joinedAt());
        String lastMessage = last.map(ChatMessage::content).orElse(null);
        LocalDateTime lastMessageAt = last.map(ChatMessage::createdAt).orElse(null);

        if (room.type() == ChatRoomType.GROUP) {
            return ChatRoomsResponse.ChatRoomSummary.group(
                    chatRoomId, otherMemberBriefs(chatRoomId, userId), lastMessage, lastMessageAt, unread);
        }
        return ChatRoomsResponse.ChatRoomSummary.direct(
                chatRoomId, toPartnerBrief(directPartnerUserId), lastMessage, lastMessageAt, unread);
    }

    /** GROUP 채팅에서 나를 제외한 현재 참여자들의 경량 정보. 나간 멤버(leftAt)·탈퇴자는 제외한다(목록이 죽지 않게). */
    private List<PartnerBrief> otherMemberBriefs(Long chatRoomId, Long userId) {
        return chatRoomMemberReader.findByChatRoomId(chatRoomId).stream()
                .filter(member -> !member.hasLeft())
                .map(ChatRoomMember::userId)
                .filter(id -> !id.equals(userId))
                .filter(id -> !userService.isWithdrawn(id))
                .map(this::toPartnerBrief)
                .toList();
    }

    /** 발신자 제외 수신자 목록(푸시 대상). DIRECT=상대 1명, GROUP=나 제외 현재 참여자(나간 멤버 제외). */
    private List<Long> recipientsFor(ChatRoom room, Long senderId) {
        Long chatRoomId = room.id();
        if (room.type() == ChatRoomType.GROUP) {
            return chatRoomMemberReader.findByChatRoomId(chatRoomId).stream()
                    .filter(member -> !member.hasLeft())
                    .map(ChatRoomMember::userId)
                    .filter(id -> !id.equals(senderId))
                    .toList();
        }
        return List.of(requireActivePartner(chatRoomId, senderId));
    }

    /** DIRECT 방만 상대 탈퇴를 검사한다(GROUP은 상대 단수 개념이 없어 통과). */
    private void requireActiveIfDirect(Long chatRoomId, Long userId) {
        if (chatRoomReader.getById(chatRoomId).type() == ChatRoomType.DIRECT) {
            requireActivePartner(chatRoomId, userId);
        }
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
