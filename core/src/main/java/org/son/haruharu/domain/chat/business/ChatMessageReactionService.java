package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.chat.business.dto.MessageReactionResponse;
import org.son.haruharu.domain.chat.business.dto.MessageReactorResponse;
import org.son.haruharu.domain.chat.business.dto.ReactionCountItem;
import org.son.haruharu.domain.chat.business.event.ReactionUpdatedEvent;
import org.son.haruharu.domain.chat.business.exception.CannotReactToDeletedMessageException;
import org.son.haruharu.domain.chat.business.exception.CannotReactToOwnMessageException;
import org.son.haruharu.domain.chat.business.exception.ChatMessageNotFoundException;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatReactionOwnerOnlyException;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReaction;
import org.son.haruharu.domain.chat.implement.ChatMessageReactionReader;
import org.son.haruharu.domain.chat.implement.ChatMessageReactionWriter;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.video.implement.ReactionType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 채팅 메시지 반응(#456, F6). 한 사람당 메시지 1개 — 없으면 생성, 같은 타입이면 해제, 다른 타입이면 교체.
 * 권한은 채팅방 멤버십(ChatRoomMember, 나가지 않음) 기준이라 GROUP/DIRECT 둘 다 같은 규칙이다. 푸시는 없다.
 * 개수는 모두에게(REACTION_UPDATED 이벤트), 누가 눌렀는지는 메시지 주인만 본다.
 */
@Service
@Transactional(readOnly = true)
public class ChatMessageReactionService {

    private final ChatRoomReader chatRoomReader;
    private final ChatRoomMemberReader chatRoomMemberReader;
    private final ChatMessageReader chatMessageReader;
    private final ChatMessageReactionReader reactionReader;
    private final ChatMessageReactionWriter reactionWriter;
    private final ChatParticipantPolicy participants;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ChatRoomLifecyclePolicy lifecycle;

    public ChatMessageReactionService(ChatRoomReader chatRoomReader, ChatRoomMemberReader chatRoomMemberReader,
                                      ChatMessageReader chatMessageReader, ChatMessageReactionReader reactionReader,
                                      ChatMessageReactionWriter reactionWriter, ChatParticipantPolicy participants,
                                      ApplicationEventPublisher eventPublisher, Clock clock, ChatRoomLifecyclePolicy lifecycle) {
        this.chatRoomReader = chatRoomReader;
        this.chatRoomMemberReader = chatRoomMemberReader;
        this.chatMessageReader = chatMessageReader;
        this.reactionReader = reactionReader;
        this.reactionWriter = reactionWriter;
        this.participants = participants;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.lifecycle = lifecycle;
    }

    @Transactional
    public MessageReactionResponse toggle(Long userId, Long chatRoomId, Long messageId, ReactionType type) {
        chatRoomReader.lockForShare(chatRoomId);
        ChatRoom room = requireActiveMember(chatRoomId, userId);
        ChatMessage message = requireMessage(chatRoomId, messageId);
        if (message.isOwnedBy(userId)) {
            throw new CannotReactToOwnMessageException();
        }
        if (message.isAdminDeleted()) {
            throw new CannotReactToDeletedMessageException();
        }
        lifecycle.requireActive(room);
        String myReaction = apply(userId, messageId, type.name());
        List<ReactionCountItem> counts = ReactionCountItem.sorted(reactionReader.countByMessageIds(List.of(messageId)));
        eventPublisher.publishEvent(new ReactionUpdatedEvent(chatRoomId, messageId, counts));
        return new MessageReactionResponse(counts, myReaction);
    }

    /** 반응을 적용하고 적용 후 내 반응을 돌려준다(해제면 null). */
    private String apply(Long userId, Long messageId, String type) {
        Optional<ChatMessageReaction> existing = reactionReader.findMine(messageId, userId);
        if (existing.isEmpty()) {
            boolean inserted = reactionWriter.insertIfAbsent(
                    ChatMessageReaction.create(messageId, userId, type, LocalDateTime.now(clock)));
            if (inserted) {
                return type;
            }
            // 같은 유저의 동시 요청이 먼저 넣었다: 다시 읽어서 "이 타입으로 누름" 의도에 맞춘다(해제하지 않음).
            existing = reactionReader.findMine(messageId, userId);
            if (existing.isEmpty() || existing.get().reactionType().equals(type)) {
                return existing.map(ChatMessageReaction::reactionType).orElse(null);
            }
            reactionWriter.save(existing.get().withType(type));
            return type;
        }
        ChatMessageReaction current = existing.get();
        if (current.reactionType().equals(type)) {
            reactionWriter.delete(current.id()); // 같은 타입 재탭 → 해제
            return null;
        }
        reactionWriter.save(current.withType(type)); // 다른 타입 → 교체
        return type;
    }

    /** 내 메시지에 반응한 사람. 메시지 주인만 볼 수 있다(아니면 403). 이름은 GROUP 가명 / DIRECT 실명. */
    public List<MessageReactorResponse> getReactors(Long userId, Long chatRoomId, Long messageId) {
        ChatRoom chatRoom = requireActiveMember(chatRoomId, userId);
        ChatMessage message = requireMessage(chatRoomId, messageId);
        if (!message.isOwnedBy(userId)) {
            throw new ChatReactionOwnerOnlyException();
        }
        return reactionReader.findByMessageId(messageId).stream()
                .map(r -> new MessageReactorResponse(participants.displayName(chatRoom, r.userId()), r.reactionType()))
                .toList();
    }

    private ChatRoom requireActiveMember(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomReader.getById(chatRoomId);
        chatRoomMemberReader.findActiveMembership(chatRoomId, userId).orElseThrow(ChatNotMemberException::new);
        participants.requireActiveIfOneToOne(chatRoomId, userId); // DIRECT: 상대 탈퇴·차단이면 토글·조회 불가(메시지 목록과 같은 규칙)
        return chatRoom;
    }

    private ChatMessage requireMessage(Long chatRoomId, Long messageId) {
        return chatMessageReader.findById(messageId)
                .filter(m -> m.chatRoomId().equals(chatRoomId))
                .orElseThrow(ChatMessageNotFoundException::new);
    }
}
