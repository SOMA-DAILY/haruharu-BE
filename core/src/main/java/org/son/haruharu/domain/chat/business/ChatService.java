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
import org.son.haruharu.domain.chat.business.exception.ChatPartnerBlockedException;
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
import org.son.haruharu.domain.chat.storage.exception.ChatRoomNotFoundException;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
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
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final org.son.haruharu.domain.chat.implement.ChatMissionPresenter missionPresenter;

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
                       ApplicationEventPublisher eventPublisher,
                       Clock clock, org.son.haruharu.domain.chat.implement.ChatMissionPresenter missionPresenter) {
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
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.missionPresenter = missionPresenter;
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
        if (chatRoomReader.existsIncludingDeletedByMatchResultId(matchResultId)) {
            return;
        }
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
        ensureGroupRoom(roomId);
    }

    /** 예약 시각이 시작 전이 배치보다 먼저 도달해도 같은 방 잠금 안에서 그룹채팅을 준비한다. */
    @Transactional
    public void ensureGroupRoom(Long roomId) {
        if (roomReader.findByIdForUpdate(roomId).isEmpty()) {
            return;
        }
        if (chatRoomReader.existsByRoomId(roomId)) {
            return;
        }
        ChatRoom room = chatRoomWriter.save(ChatRoom.createGroup(roomId));
        LocalDateTime now = LocalDateTime.now(clock);
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
        chatRoomReader.lockForUpdate(chatRoomId);
        requireMember(chatRoomId, senderId);
        ChatRoom chatRoom = chatRoomReader.getById(chatRoomId);
        List<Long> recipientIds = recipientsFor(chatRoom, senderId); // DIRECT=상대 1명, GROUP=나 제외 전원
        ChatMessage saved = chatMessageWriter.save(ChatMessage.create(chatRoomId, senderId, content));
        // 발신자 표시 이름(#393): GROUP은 방 세션 가명, DIRECT는 매칭쌍이라 실명. 푸시 본문("OO님이…")에 그대로 실린다.
        String senderNickname = chatDisplayName(chatRoom, senderId);
        eventPublisher.publishEvent(new ChatMessageSentEvent(
                saved.id(), chatRoomId, chatRoom.type(), chatRoom.roomId(), senderId,
                recipientIds, senderNickname, saved.createdAt()));
        ChatMessageBroadcast broadcast = ChatMessageBroadcast.from(saved);
        eventPublisher.publishEvent(new ChatMessageBroadcastEvent(broadcast));
        return broadcast;
    }

    /** 내 채팅방 목록. 마지막 메시지 최신순으로 정렬한다. 차단 관계 필터 세트는 루프 밖에서 한 번만 조회한다(#408, N+1 방지). */
    public ChatRoomsResponse getMyRooms(Long userId) {
        Set<Long> myBlocked = blockReader.getBlockedUserIds(userId);
        Set<Long> blockRelated = blockReader.getRelatedUserIds(userId);
        List<ChatRoomsResponse.ChatRoomSummary> summaries = chatRoomMemberReader.findActiveByUserId(userId).stream()
                .map(me -> toSummary(me, userId, myBlocked, blockRelated))
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
            return ChatRoomDetailResponse.group(chatRoomId, otherMemberBriefs(room.roomId(), chatRoomId, userId), room.createdAt());
        }
        Long partnerUserId = requireActivePartner(chatRoomId, userId);
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
        requireActiveIfDirect(chatRoomId, userId);
        int limit = Math.min(Math.max(size, 1), MAX_MESSAGE_PAGE_SIZE);
        List<ChatMessage> messages = chatMessageReader.findPage(chatRoomId, cursor, limit);
        return MessagePageResponse.of(messages, limit, missionPresenter.prepare(messages.stream().map(ChatMessage::visibleMission).toList()));
    }

    public org.son.haruharu.domain.mission.business.dto.MissionImageResponse getMissionImage(Long chatRoomId, Long userId, Long messageId) {
        requireMember(chatRoomId,userId);
        requireActiveIfDirect(chatRoomId,userId);
        var message = chatMessageReader.findById(messageId).filter(item -> item.chatRoomId().equals(chatRoomId));
        return message.map(item -> missionPresenter.present(item.visibleMission()))
                .map(org.son.haruharu.domain.chat.implement.ChatMission::image)
                .orElseThrow(org.son.haruharu.domain.chat.storage.exception.ChatMissionImageNotFoundException::new);
    }

    /** 읽음 커서 갱신. 커서는 뒤로만 이동한다(역행 방지). */
    @Transactional
    public ReadResponse markRead(Long chatRoomId, Long userId, Long lastReadMessageId) {
        // 나가기와 읽음 갱신이 경합해 이전 leftAt=null 값으로 퇴장을 덮어쓰지 않게 한다.
        chatRoomReader.lockForUpdate(chatRoomId);
        ChatRoomMember member = requireMember(chatRoomId, userId);
        requireActiveIfDirect(chatRoomId, userId);
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

    /** null 반환 = 목록에서 제외(차단 관계 DIRECT 방, #408). */
    private ChatRoomsResponse.ChatRoomSummary toSummary(ChatRoomMember me, Long userId,
                                                        Set<Long> myBlocked, Set<Long> blockRelated) {
        Long chatRoomId = me.chatRoomId();
        Optional<ChatRoom> found = chatRoomReader.findById(chatRoomId);
        // 활성 멤버 조회 직후 다른 트랜잭션이 종료했을 때 목록 전체를 실패시키지 않는다.
        if (found.isEmpty()) return null;
        ChatRoom room = found.orElseThrow(ChatRoomNotFoundException::new);

        // DIRECT 상대 탈퇴는 마지막 메시지 조회 전에 걸러야 목록 전체가 죽지 않는다(#255).
        Long directPartnerUserId = null;
        Set<Long> excludedSenders = myBlocked; // GROUP 미리보기: 내가 차단한 상대만 제외(단방향)
        if (room.type() == ChatRoomType.DIRECT) {
            directPartnerUserId = partnerUserId(chatRoomId, userId);
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
        String lastMessage = last.map(ChatMessage::visibleContent).orElse(null);
        LocalDateTime lastMessageAt = last.map(ChatMessage::createdAt).orElse(null);

        if (room.type() == ChatRoomType.GROUP) {
            return ChatRoomsResponse.ChatRoomSummary.group(
                    chatRoomId, otherMemberBriefs(room.roomId(), chatRoomId, userId), lastMessage, lastMessageAt, unread);
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

    /** 상대 프로필에서 목록·헤더용 경량 정보를 뽑는다. 대표사진 = sorted_order 최소 이미지(없으면 null). DIRECT(매칭쌍) 전용이라 실명. */
    private PartnerBrief toPartnerBrief(Long partnerUserId) {
        var profile = profileService.getProfile(partnerUserId);
        return new PartnerBrief(partnerUserId, profile.nickname(), representativeImageUrl(profile));
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

    /** 채팅 표시 이름. GROUP은 방 세션 가명(없으면 익명 플레이스홀더) — 매칭 전 실명 노출 금지(#393). DIRECT는 매칭쌍이라 실명. */
    private String chatDisplayName(ChatRoom chatRoom, Long userId) {
        if (chatRoom.type() == ChatRoomType.GROUP) {
            return roomMemberReader.findByRoomIdAndUserId(chatRoom.roomId(), userId)
                    .map(RoomMember::displayPseudonym)
                    .orElse(RoomMember.ANONYMOUS_NAME);
        }
        return profileService.getNickname(userId);
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
        // 차단 관계면 어느 쪽이 차단했든 양방향으로 접근·전송을 막는다(#408).
        if (blockReader.isBlockedBetween(userId, partnerUserId)) {
            throw new ChatPartnerBlockedException();
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
