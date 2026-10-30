package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.dto.ChatRoomDetailResponse;
import org.son.haruharu.domain.chat.business.dto.ChatRoomsResponse;
import org.son.haruharu.domain.chat.business.dto.GroupChatRoomResponse;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse;
import org.son.haruharu.domain.chat.business.dto.PartnerBrief;
import org.son.haruharu.domain.chat.business.dto.ReadResponse;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.business.event.ChatMessageBroadcastEvent;
import org.son.haruharu.domain.chat.business.event.ChatRoomClosedEvent;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatReplyInvalidException;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatMessageWriter;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.chat.implement.PairChatSchedule;
import org.son.haruharu.domain.chat.implement.PairChatWindow;
import org.son.haruharu.domain.chat.business.exception.PairChatClosedException;
import org.son.haruharu.domain.chat.business.exception.PairChatLeaveNotAllowedException;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberWriter;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.ChatRoomWriter;
import org.son.haruharu.domain.chat.storage.exception.ChatRoomNotFoundException;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final RoomMemberReader roomMemberReader;
    private final RoomReader roomReader;
    private final BlockReader blockReader;
    private final ChatMessageViews chatMessageViews;
    private final ChatParticipantPolicy participants;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ChatRoomLifecyclePolicy lifecycle;
    private final PairChatSchedule pairChatSchedule;

    public ChatService(ChatRoomReader chatRoomReader,
                       ChatRoomWriter chatRoomWriter,
                       ChatRoomMemberReader chatRoomMemberReader,
                       ChatRoomMemberWriter chatRoomMemberWriter,
                       ChatMessageReader chatMessageReader,
                       ChatMessageWriter chatMessageWriter,
                       ProfileService profileService,
                       UserService userService,
                       RoomMemberReader roomMemberReader,
                       RoomReader roomReader,
                       BlockReader blockReader,
                       ChatMessageViews chatMessageViews,
                       ChatParticipantPolicy participants,
                       ApplicationEventPublisher eventPublisher,
                       Clock clock, ChatRoomLifecyclePolicy lifecycle,
                       PairChatSchedule pairChatSchedule) {
        this.chatRoomReader = chatRoomReader;
        this.chatRoomWriter = chatRoomWriter;
        this.chatRoomMemberReader = chatRoomMemberReader;
        this.chatRoomMemberWriter = chatRoomMemberWriter;
        this.chatMessageReader = chatMessageReader;
        this.chatMessageWriter = chatMessageWriter;
        this.profileService = profileService;
        this.userService = userService;
        this.roomMemberReader = roomMemberReader;
        this.roomReader = roomReader;
        this.blockReader = blockReader;
        this.chatMessageViews = chatMessageViews;
        this.participants = participants;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.lifecycle = lifecycle;
        this.pairChatSchedule = pairChatSchedule;
    }

    /**
     * 상호매칭 성사 시 두 유저의 1:1 채팅방을 생성한다(#61, MutualMatchEvent 커밋 후 호출).
     * match_result_id 당 1방 멱등 — 이벤트 재발행·재시도에도 중복 생성하지 않는다(unique + 사전 검사).
     *
     * AFTER_COMMIT 리스너에서 호출되므로 REQUIRES_NEW로 새 트랜잭션을 연다(#158).
     * 원본(최종선택) 트랜잭션은 이미 커밋된 상태라, REQUIRED면 커밋된 트랜잭션에 join되어 INSERT가 유실된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createRoomFromMatch(Long sourceRoomId, Long matchResultId, Long userIdA, Long userIdB) {
        if(chatRoomReader.existsIncludingDeletedByMatchResultId(matchResultId)) return;
        if(roomReader.findByIdForUpdate(sourceRoomId).isEmpty()) return;
        if (chatRoomReader.existsIncludingDeletedByMatchResultId(matchResultId)) return;
        if(roomMemberReader.findByRoomIdAndUserId(sourceRoomId,userIdA).isEmpty()
                || roomMemberReader.findByRoomIdAndUserId(sourceRoomId,userIdB).isEmpty()) return;
        ChatRoom room = chatRoomWriter.save(ChatRoom.createDirect(matchResultId));
        LocalDateTime now = LocalDateTime.now(clock);
        chatRoomMemberWriter.save(ChatRoomMember.join(room.id(), userIdA, now));
        chatRoomMemberWriter.save(ChatRoomMember.join(room.id(), userIdB, now));
    }

    /**
     * 소개팅방 시작 시 방 전체 그룹채팅(GROUP)을 생성한다(RoomStartedEvent 커밋 후 호출).
     * 입장과 같은 방 잠금 안에서 멤버를 조회하여 생성 중 입장한 멤버도 누락되지 않게 한다.
     * room_id 당 1방 멱등 — 이벤트 재발행에도 중복 생성하지 않는다.
     *
     * AFTER_COMMIT 리스너에서 호출되므로 REQUIRES_NEW로 새 트랜잭션을 연다(#158, DIRECT와 동일).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createGroupRoom(Long roomId) {
        var source = roomReader.findByIdForUpdate(roomId).orElse(null);
        if (source == null) {
            return;
        }
        if (chatRoomReader.existsByRoomId(roomId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ChatRoom group = ChatRoom.createGroup(roomId);
        // 지연된 시작 이벤트가 종료·취소 뒤 도착해도 채팅을 다시 열지 않는다.
        if (source.status() == RoomStatus.ENDED
                || source.status() == RoomStatus.CANCELLED) {
            group = group.close(now);
        }
        ChatRoom room = chatRoomWriter.save(group);
        roomMemberReader.getByRoomId(roomId).forEach(member ->
                chatRoomMemberWriter.save(ChatRoomMember.join(room.id(), member.userId(), now)));
    }

    /** 입장 트랜잭션에 참여한다. 채팅방 생성 전이면 생성 시점의 잠금 내 스냅샷이 등록을 담당한다. */
    @Transactional
    public void addGroupRoomMember(Long roomId, Long userId, LocalDateTime joinedAt) {
        if (roomReader.findByIdForUpdate(roomId).isEmpty()) {
            return;
        }
        chatRoomReader.findByRoomId(roomId).ifPresent(group -> {
            if (chatRoomMemberReader.findMembership(group.id(), userId).isEmpty()) {
                chatRoomMemberWriter.save(ChatRoomMember.join(group.id(), userId, joinedAt));
            }
        });
    }

    /**
     * 메시지 전송(#60 STOMP). 발신자가 방 참여자인지 검증 후 저장하고, 브로드캐스트 payload를 반환한다.
     * 실제 브로드캐스트(convertAndSend)는 커밋 후 application 계층(ChatLifecycleEventListener)이 담당한다.
     */
    @Transactional
    public ChatMessageBroadcast sendMessage(Long chatRoomId, Long senderId, String content) {
        return sendMessage(chatRoomId, senderId, content, null);
    }

    /**
     * 메시지 전송. replyToMessageId 가 있으면 답장(#456): 원문이 같은 채팅방 메시지여야 하고(아니면 CHAT_REPLY_INVALID),
     * 원문 보낸 사람 표시 이름과 앞 50자를 스냅샷으로 남긴다. 내 메시지에 답장하는 것도 허용한다.
     */
    @Transactional
    public ChatMessageBroadcast sendMessage(Long chatRoomId, Long senderId, String content, Long replyToMessageId) {
        chatRoomReader.lockForUpdate(chatRoomId);
        requireMember(chatRoomId, senderId);
        ChatRoom chatRoom = chatRoomReader.getById(chatRoomId);
        List<Long> recipientIds = recipientsFor(chatRoom, senderId); // DIRECT=상대 1명, GROUP=나 제외 전원
        ChatMessage original = replyToMessageId == null ? null : requireReplyable(chatRoomId, replyToMessageId);
        ChatMessage toSave = original == null
                ? ChatMessage.create(chatRoomId, senderId, content)
                : ChatMessage.reply(chatRoomId, senderId, content, original, replySenderName(chatRoom, original));
        lifecycle.requireActive(chatRoom);
        // 대화 시간 1:1(#540)은 운영자가 정한 시간에만 보낼 수 있다. 방 종료는 위 requireActive 가 먼저 막는다.
        if (chatRoom.type() == ChatRoomType.PAIR
                && !pairChatSchedule.windowAt(chatRoom.roomId(), LocalDateTime.now(clock)).isOpen()) {
            throw new PairChatClosedException();
        }
        ChatMessage saved = chatMessageWriter.save(toSave);
        // 발신자 표시 이름(#393): GROUP·PAIR는 방 세션 가명, DIRECT는 매칭쌍이라 실명. 푸시 본문("OO님이…")에 그대로 실린다.
        String senderNickname = participants.displayName(chatRoom, senderId);
        eventPublisher.publishEvent(new ChatMessageSentEvent(
                saved.id(), chatRoomId, chatRoom.type(), chatRoom.roomId(), senderId,
                recipientIds, senderNickname, saved.createdAt(), original == null ? null : original.senderId()));
        ChatMessageBroadcast broadcast = ChatMessageBroadcast.from(saved);
        if (original != null && original.photo() != null) {
            var photo = original.photo();
            broadcast = broadcast.withReplyPhoto(new org.son.haruharu.domain.chat.business.dto.ChatPhotoView(
                    photo.key(), null, photo.width(), photo.height()));
        }
        eventPublisher.publishEvent(new ChatMessageBroadcastEvent(broadcast));
        return broadcast;
    }

    /** 시스템 안내의 인용 이름은 유저 프로필 조회 없이 '하루'로 표시한다. */
    private String replySenderName(ChatRoom chatRoom, ChatMessage original) {
        return original.isSystem() ? original.senderName() : participants.displayName(chatRoom, original.senderId());
    }

    /** 답장 원문: 같은 채팅방의 (삭제되지 않은, 운영자가 지우지 않은) 메시지여야 한다. */
    private ChatMessage requireReplyable(Long chatRoomId, Long replyToMessageId) {
        return chatMessageReader.findById(replyToMessageId)
                .filter(original -> original.chatRoomId().equals(chatRoomId))
                .filter(original -> !original.isAdminDeleted())
                .orElseThrow(ChatReplyInvalidException::new);
    }

    /**
     * 내 채팅방 목록. 마지막 메시지 최신순으로 정렬한다. 차단 관계 필터 세트는 루프 밖에서 한 번만 조회한다(#408, N+1 방지).
     * includePair 면 대화 시간 1:1(PAIR) 방도 넣는다(메시지 없는 방·끝난 방·상대 탈퇴·차단 관계 방 제외).
     */
    public ChatRoomsResponse getMyRooms(Long userId, boolean includePair) {
        Set<Long> myBlocked = blockReader.getBlockedUserIds(userId);
        Set<Long> blockRelated = blockReader.getRelatedUserIds(userId);
        List<ChatRoomsResponse.ChatRoomSummary> summaries = chatRoomMemberReader.findActiveByUserId(userId).stream()
                .map(me -> toSummary(me, userId, myBlocked, blockRelated, includePair))
                .filter(Objects::nonNull)
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
            return ChatRoomDetailResponse.group(chatRoomId, otherMemberBriefs(room.roomId(), chatRoomId, userId),
                    room.createdAt(), room.roomId());
        }
        Long partnerUserId = participants.requireActivePartner(chatRoomId, userId);
        if (room.type() == ChatRoomType.PAIR) {
            return ChatRoomDetailResponse.pair(chatRoomId, toPseudonymBrief(room.roomId(), partnerUserId),
                    room.createdAt(), room.roomId(), pairChatState(room));
        }
        return ChatRoomDetailResponse.direct(chatRoomId, toPartnerBrief(partnerUserId), room.createdAt());
    }

    /** 소개팅방 id로 연결된 GROUP 채팅을 찾고 현재 활성 채팅 멤버에게만 id를 반환한다. */
    public GroupChatRoomResponse getGroupRoomBySourceRoom(Long roomId, Long userId) {
        ChatRoom room = chatRoomReader.getByRoomId(roomId);
        requireMember(room.id(), userId);
        return new GroupChatRoomResponse(room.id());
    }

    /** 과거 메시지(최신순 커서 페이지). 삭제된 메시지는 제외. */
    public MessagePageResponse getMessages(Long chatRoomId, Long userId, Long cursor, int size) {
        requireMember(chatRoomId, userId);
        participants.requireActiveIfOneToOne(chatRoomId, userId);
        int limit = Math.min(Math.max(size, 1), MAX_MESSAGE_PAGE_SIZE);
        List<ChatMessage> messages = chatMessageReader.findPage(chatRoomId, cursor, limit);
        return MessagePageResponse.of(messages, limit, chatMessageViews.toItems(messages, userId));
    }

    /** 읽음 커서 갱신. 커서는 뒤로만 이동한다(역행 방지). */
    @Transactional
    public ReadResponse markRead(Long chatRoomId, Long userId, Long lastReadMessageId) {
        // 나가기와 읽음 갱신이 경합해 이전 leftAt=null 값으로 퇴장을 덮어쓰지 않게 한다.
        chatRoomReader.lockForUpdate(chatRoomId);
        ChatRoomMember member = requireMember(chatRoomId, userId);
        participants.requireActiveIfOneToOne(chatRoomId, userId);
        ChatRoomMember updated = chatRoomMemberWriter.save(member.readUpTo(lastReadMessageId));
        // 안읽음 수에서 내 메시지 + 내가 차단한 상대의 메시지를 제외한다(#408 — 그룹채팅 반영, 단방향).
        Set<Long> excludedSenders = new HashSet<>(blockReader.getBlockedUserIds(userId));
        excludedSenders.add(userId);
        long unread = chatMessageReader.countUnread(chatRoomId, updated.lastReadMessageId(), excludedSenders);
        return new ReadResponse(chatRoomId, unread);
    }

    /** DIRECT는 양쪽 참여 종료·방 소프트 삭제, GROUP은 기존 개인 퇴장. */
    @Transactional
    public void leaveRoom(Long chatRoomId, Long userId) {
        chatRoomReader.lockForUpdate(chatRoomId);
        ChatRoomMember member = chatRoomMemberReader.findMembership(chatRoomId, userId)
                .orElseThrow(ChatNotMemberException::new);
        Optional<ChatRoom> room = chatRoomReader.findById(chatRoomId);
        if (room.isEmpty()) {
            if (member.hasLeft()) return;
            throw new ChatRoomNotFoundException();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (room.orElseThrow(ChatRoomNotFoundException::new).type() == ChatRoomType.PAIR) {
            // 대화 시간 1:1 은 방이 끝나면 함께 닫힌다. 나가기(DIRECT 처럼 방 삭제)는 없다(#540).
            throw new PairChatLeaveNotAllowedException();
        }
        if (room.orElseThrow(ChatRoomNotFoundException::new).type() == ChatRoomType.GROUP) {
            if (member.hasLeft()) throw new ChatNotMemberException();
            chatRoomMemberWriter.save(member.leave(now));
            return;
        }
        List<ChatRoomMember> members = chatRoomMemberReader.findByChatRoomId(chatRoomId);
        members.forEach(participant -> chatRoomMemberWriter.save(participant.leave(now)));
        chatRoomWriter.delete(chatRoomId);
        eventPublisher.publishEvent(new ChatRoomClosedEvent(chatRoomId,
                members.stream().map(ChatRoomMember::userId).toList()));
    }

    /** null 반환 = 목록에서 제외(차단 관계 DIRECT 방 #408, 조건에 안 맞는 PAIR 방 #540). */
    private ChatRoomsResponse.ChatRoomSummary toSummary(ChatRoomMember me, Long userId,
                                                        Set<Long> myBlocked, Set<Long> blockRelated,
                                                        boolean includePair) {
        Long chatRoomId = me.chatRoomId();
        Optional<ChatRoom> found = chatRoomReader.findById(chatRoomId);
        // 활성 멤버 조회 직후 다른 트랜잭션이 종료했을 때 목록 전체를 실패시키지 않는다.
        if (found.isEmpty()) return null;
        ChatRoom room = found.orElseThrow(ChatRoomNotFoundException::new);
        if (room.type() == ChatRoomType.PAIR) {
            return includePair ? toPairSummary(me, userId, room, blockRelated) : null;
        }

        // DIRECT 상대 탈퇴는 마지막 메시지 조회 전에 걸러야 목록 전체가 죽지 않는다(#255).
        Long directPartnerUserId = null;
        Set<Long> excludedSenders = myBlocked; // GROUP 미리보기: 내가 차단한 상대만 제외(단방향)
        if (room.type() == ChatRoomType.DIRECT) {
            directPartnerUserId = participants.partnerUserId(chatRoomId, userId);
            // 차단 관계(양방향)인 DIRECT 방은 목록에서 제외한다(#408 — 기록은 보존).
            if (blockRelated.contains(directPartnerUserId)) {
                return null;
            }
            if (userService.isWithdrawn(directPartnerUserId)) {
                return ChatRoomsResponse.ChatRoomSummary.withdrawn(chatRoomId);
            }
            excludedSenders = Set.of(); // 차단 방은 위에서 걸렀으므로 DIRECT 미리보기엔 필터 불필요
        }

        Set<Long> unreadExcluded = new HashSet<>(excludedSenders);
        unreadExcluded.add(userId);
        Optional<ChatMessage> last = chatMessageReader.findLast(chatRoomId, excludedSenders);
        long unread = chatMessageReader.countUnread(chatRoomId, me.lastReadMessageId(), unreadExcluded);
        String lastMessage = last.map(ChatMessage::displayContent).orElse(null);
        LocalDateTime lastMessageAt = last.map(ChatMessage::createdAt).orElse(null);

        if (room.type() == ChatRoomType.GROUP) {
            return ChatRoomsResponse.ChatRoomSummary.group(
                    chatRoomId, otherMemberBriefs(room.roomId(), chatRoomId, userId), lastMessage, lastMessageAt, unread,
                    room.roomId());
        }
        return ChatRoomsResponse.ChatRoomSummary.direct(
                chatRoomId, toPartnerBrief(directPartnerUserId), lastMessage, lastMessageAt, unread);
    }

    /** GROUP 채팅에서 나를 제외한 현재 참여자들의 경량 정보. 나간 멤버(leftAt)·탈퇴자는 제외한다(목록이 죽지 않게). */
    private List<PartnerBrief> otherMemberBriefs(Long sourceRoomId, Long chatRoomId, Long userId) {
        // 소개팅 방 멤버를 한 번에 읽어 가명 조회를 배치화한다(참여자별 findByRoomIdAndUserId N+1 방지).
        Map<Long, RoomMember> membersByUserId = roomMemberReader.getByRoomId(sourceRoomId).stream()
                .collect(Collectors.toMap(RoomMember::userId, m -> m, (a, b) -> a));
        return chatRoomMemberReader.findByChatRoomId(chatRoomId).stream()
                .filter(member -> !member.hasLeft())
                .map(ChatRoomMember::userId)
                .filter(id -> !id.equals(userId))
                .filter(id -> !userService.isWithdrawn(id))
                .map(id -> toGroupMemberBrief(membersByUserId.get(id), id))
                .toList();
    }

    /**
     * PAIR 목록 한 줄(#540). 방이 끝나 닫힌 방, 메시지가 하나도 없는 방(열기만 하고 안 보낸 방),
     * 상대가 탈퇴했거나 차단 관계(양방향)인 방은 목록에 넣지 않는다.
     */
    private ChatRoomsResponse.ChatRoomSummary toPairSummary(ChatRoomMember me, Long userId, ChatRoom room,
                                                            Set<Long> blockRelated) {
        if (room.closeAt() != null) return null;
        // 상대 멤버 행이 없으면 목록 전체를 실패시키지 않고 이 줄만 뺀다.
        Long partnerUserId = chatRoomMemberReader.findByChatRoomId(room.id()).stream()
                .map(ChatRoomMember::userId).filter(id -> !id.equals(userId)).findFirst().orElse(null);
        if (partnerUserId == null || blockRelated.contains(partnerUserId)
                || userService.isWithdrawn(partnerUserId)) return null;
        ChatMessage last = chatMessageReader.findLast(room.id(), Set.of()).orElse(null);
        if (last == null) return null;
        long unread = chatMessageReader.countUnread(room.id(), me.lastReadMessageId(), Set.of(userId));
        return ChatRoomsResponse.ChatRoomSummary.pair(room.id(), toPseudonymBrief(room.roomId(), partnerUserId),
                last.displayContent(), last.createdAt(), unread, room.roomId());
    }

    /** PAIR 상세의 대화 시간 상태. 방이 끝나 닫힌 방은 다시 열리지 않는다. */
    private ChatRoomDetailResponse.PairChatState pairChatState(ChatRoom room) {
        if (room.closeAt() != null) return ChatRoomDetailResponse.PairChatState.CLOSED;
        PairChatWindow window = pairChatSchedule.windowAt(room.roomId(), LocalDateTime.now(clock));
        return switch (window.state()) {
            case OPEN -> new ChatRoomDetailResponse.PairChatState(true, window.closesAt(), null);
            case SCHEDULED -> new ChatRoomDetailResponse.PairChatState(false, null, window.opensAt());
            case NONE -> ChatRoomDetailResponse.PairChatState.CLOSED;
        };
    }

    /** 발신자 제외 수신자 목록(푸시 대상). DIRECT·PAIR=상대 1명, GROUP=나 제외 현재 참여자(나간 멤버 제외). */
    private List<Long> recipientsFor(ChatRoom room, Long senderId) {
        Long chatRoomId = room.id();
        if (room.type() == ChatRoomType.GROUP) {
            return chatRoomMemberReader.findByChatRoomId(chatRoomId).stream()
                    .filter(member -> !member.hasLeft())
                    .map(ChatRoomMember::userId)
                    .filter(id -> !id.equals(senderId))
                    .toList();
        }
        return List.of(participants.requireActivePartner(chatRoomId, senderId));
    }

    /** 상대 프로필에서 목록·헤더용 경량 정보를 뽑는다. 대표사진 = sorted_order 최소 이미지(없으면 null). DIRECT(매칭쌍) 전용이라 실명. */
    private PartnerBrief toPartnerBrief(Long partnerUserId) {
        var profile = profileService.getProfile(partnerUserId);
        return new PartnerBrief(partnerUserId, profile.nickname(), representativeImageUrl(profile));
    }

    /** PAIR 상대 경량 정보. GROUP 과 같이 방 세션 가명으로 가린다(#540, 매칭 전 실명 노출 금지 #393). */
    private PartnerBrief toPseudonymBrief(Long sourceRoomId, Long memberUserId) {
        return toGroupMemberBrief(roomMemberReader.findByRoomIdAndUserId(sourceRoomId, memberUserId).orElse(null),
                memberUserId);
    }

    /** GROUP 참여자 경량 정보. 이름은 방 세션 가명(없으면 익명 플레이스홀더)으로 가린다 — 매칭 전 실명 노출 금지(#393). */
    private PartnerBrief toGroupMemberBrief(RoomMember roomMember, Long memberUserId) {
        var profile = profileService.getProfile(memberUserId);
        String displayName = roomMember != null ? roomMember.displayPseudonym() : RoomMember.ANONYMOUS_NAME;
        return new PartnerBrief(memberUserId, displayName, representativeImageUrl(profile));
    }

    private String representativeImageUrl(ProfileResponse profile) {
        return profile.images().stream()
                .min(Comparator.comparingInt(img -> img.sortedOrder()))
                .map(img -> img.imageUrl())
                .orElse(null);
    }

    /** 방 존재(404) 확인 후 요청자가 참여자(403)인지 검증하고 멤버십을 반환한다. */
    private ChatRoomMember requireMember(Long chatRoomId, Long userId) {
        chatRoomReader.getById(chatRoomId);
        return chatRoomMemberReader.findMembership(chatRoomId, userId)
                .filter(member -> !member.hasLeft())
                .orElseThrow(ChatNotMemberException::new);
    }
}
