package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse.MessageItem;
import org.son.haruharu.domain.chat.business.dto.ReactionCountItem;
import org.son.haruharu.domain.chat.business.dto.ReplyToView;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMissionLookup;
import org.son.haruharu.domain.chat.implement.ChatMissionReference;
import org.son.haruharu.domain.chat.implement.ChatMessageReactionReader;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.MessageReactionCount;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 메시지 목록 항목 조립(#456). 한 페이지의 반응 집계는 쿼리 1번, 내 반응 1번, 답장 원문 1번으로 끝낸다(건별 조회 금지).
 * 호출하는 Service 의 트랜잭션 안에서만 쓴다.
 */
@Component
public class ChatMessageViews {

    private final ChatMessageReactionReader reactionReader;
    private final ChatMessageReader messageReader;
    private final BlockReader blockReader;
    private final ChatMissionLookup missionLookup;

    public ChatMessageViews(ChatMessageReactionReader reactionReader, ChatMessageReader messageReader,
                            BlockReader blockReader, ChatMissionLookup missionLookup) {
        this.reactionReader = reactionReader;
        this.messageReader = messageReader;
        this.blockReader = blockReader;
        this.missionLookup = missionLookup;
    }

    public List<MessageItem> toItems(List<ChatMessage> messages, Long viewerId) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Long> ids = messages.stream().map(ChatMessage::id).toList();
        Map<Long, List<MessageReactionCount>> counts = reactionReader.countByMessageIds(ids).stream()
                .collect(Collectors.groupingBy(MessageReactionCount::messageId));
        Map<Long, String> mine = reactionReader.findMyTypes(ids, viewerId);

        List<Long> replyIds = messages.stream().map(ChatMessage::replyToMessageId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, ChatMessage> originals = replyIds.isEmpty() ? Map.of()
                : messageReader.findAllByIds(replyIds).stream()
                        .collect(Collectors.toMap(ChatMessage::id, Function.identity()));
        Set<Long> blocked = replyIds.isEmpty() ? Set.of() : blockReader.getBlockedUserIds(viewerId);
        List<Long> announcementIds = messages.stream().filter(m -> !m.isAdminDeleted())
                .map(ChatMessage::missionAnnouncementId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ChatMissionReference> missions = announcementIds.isEmpty() ? Map.of()
                : missionLookup.findByAnnouncementIds(announcementIds);

        return messages.stream()
                .map(m -> MessageItem.of(m,
                        ReactionCountItem.sorted(counts.getOrDefault(m.id(), List.of())),
                        mine.get(m.id()),
                        replyTo(m, originals, blocked),
                        m.missionAnnouncementId() == null ? null : missions.get(m.missionAnnouncementId())))
                .toList();
    }

    private ReplyToView replyTo(ChatMessage message, Map<Long, ChatMessage> originals, Set<Long> blocked) {
        if (message.replyToMessageId() == null) {
            return null;
        }
        ChatMessage original = originals.get(message.replyToMessageId());
        boolean available = isViewable(original, blocked);
        return ReplyToView.of(message.replyToMessageId(), message.replyPreviewSender(),
                message.replyPreviewContent(), available);
    }

    /** 원문이 남아 있고(운영자 삭제 아님, #457) 조회자가 원문 보낸 사람을 차단하지 않았을 때 볼 수 있다. */
    static boolean isViewable(ChatMessage original, Set<Long> blockedByViewer) {
        return original != null && !original.isAdminDeleted()
                && (original.isSystem() || !blockedByViewer.contains(original.senderId()));
    }
}
