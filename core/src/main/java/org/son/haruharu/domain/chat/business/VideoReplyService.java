package org.son.haruharu.domain.chat.business;

import lombok.RequiredArgsConstructor;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.dto.*;
import org.son.haruharu.domain.chat.business.event.*;
import org.son.haruharu.domain.chat.business.exception.*;
import org.son.haruharu.domain.chat.implement.*;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.user.business.UserService;
import org.son.haruharu.domain.video.implement.*;
import org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoReplyService {
    private final ChatRoomReader rooms;
    private final ChatRoomMemberReader members;
    private final ChatMessageReader messages;
    private final ChatMessageWriter writer;
    private final RoomMemberReader roomMembers;
    private final RoomReader sourceRooms;
    private final VideoPostReader videos;
    private final BlockReader blocks;
    private final UserService users;
    private final S3StoragePort storage;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public ChatMessageBroadcast send(
            Long roomId, Long videoId, Long senderId, String content, String requestId) {
        if (content == null || content.isBlank() || content.length() > 2000)
            throw new VideoReplyInvalidException("답장은 1~2000자로 입력해 주세요.");
        try {
            if (!UUID.fromString(requestId).toString().equals(requestId))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new VideoReplyInvalidException("잘못된 전송 요청입니다.");
        }
        ChatRoom chat = rooms.getByRoomId(roomId);
        // Same lock as normal sends / room closure: serialize idempotency lookup and insert.
        rooms.lockForUpdate(chat.id());
        requireMember(chat.id(), senderId);
        var previous = messages.findByRequest(chat.id(), senderId, requestId);
        if (previous.isPresent()) {
            var saved = previous.get();
            if (!Objects.equals(saved.videoPostId(), videoId)
                    || !saved.content().equals(content.strip()))
                throw new VideoReplyInvalidException("이미 사용한 전송 요청입니다.");
            return ChatMessageBroadcast.from(saved);
        }
        Room room = sourceRooms.findById(roomId).orElseThrow(RoomNotFoundException::new);
        RoomStatus status = room.statusAt(LocalDateTime.now(clock));
        if (status != RoomStatus.ACTIVE && status != RoomStatus.SELECTING)
            throw new VideoReplyInvalidException("답장을 보낼 수 없는 방입니다.");
        VideoPost post = videos.getById(videoId);
        RoomMember owner = roomMembers.getById(post.roomMemberId());
        if (!owner.roomId().equals(roomId) || post.deletedAt() != null || post.isHidden())
            throw new VideoPostNotFoundException();
        if (owner.userId().equals(senderId))
            throw new VideoReplyInvalidException("본인 영상에는 답장할 수 없어요.");
        if (blocks.isBlockedBetween(senderId, owner.userId()))
            throw new ChatPartnerBlockedException();
        if (users.isWithdrawn(owner.userId())) throw new ChatPartnerWithdrawnException();
        requireMember(chat.id(), owner.userId());
        RoomMember sender = roomMembers.getByRoomIdAndUserId(roomId, senderId);
        ChatMessage saved =
                writer.save(
                        new ChatMessage(
                                null,
                                chat.id(),
                                senderId,
                                content.strip(),
                                null,
                                videoId,
                                requestId));
        ChatMessageBroadcast broadcast = ChatMessageBroadcast.from(saved);
        events.publishEvent(new ChatMessageBroadcastEvent(broadcast));
        // Deliberately not ChatMessageSentEvent: only the video owner receives a push.
        events.publishEvent(
                new VideoReplySentEvent(
                        chat.id(), owner.userId(), senderId, sender.displayPseudonym()));
        return broadcast;
    }

    public VideoReplyPreview previewForChat(Long chatId, Long videoId, Long userId) {
        requireMember(chatId, userId);
        ChatRoom chat = rooms.getById(chatId);
        if (chat.type() != ChatRoomType.GROUP)
            throw new VideoReplyInvalidException("단체 채팅에서만 볼 수 있어요.");
        return preview(chat.roomId(), videoId, userId);
    }

    public VideoReplyPreview preview(Long roomId, Long videoId, Long userId) {
        ChatRoom chat = rooms.getByRoomId(roomId);
        requireMember(chat.id(), userId);
        VideoPost post;
        try {
            post = videos.getById(videoId);
        } catch (VideoPostNotFoundException e) {
            return unavailable(videoId, "DELETED");
        }
        RoomMember owner = roomMembers.getById(post.roomMemberId());
        if (!owner.roomId().equals(roomId)) throw new VideoPostNotFoundException();
        if (post.deletedAt() != null) return unavailable(videoId, "DELETED");
        if (post.isHidden()
                || blocks.isBlockedBetween(userId, owner.userId())
                || users.isWithdrawn(owner.userId())) return unavailable(videoId, "UNAVAILABLE");
        if (members.findMembership(chat.id(), owner.userId()).filter(m -> !m.hasLeft()).isEmpty())
            return unavailable(videoId, "UNAVAILABLE");
        return new VideoReplyPreview(
                videoId,
                "AVAILABLE",
                storage.createPresignedGetUrl(post.videoUrl()),
                storage.createPresignedGetUrl(post.thumbnailUrl()),
                post.caption(),
                owner.displayPseudonym());
    }

    private VideoReplyPreview unavailable(Long id, String state) {
        return new VideoReplyPreview(id, state, null, null, null, null);
    }

    private void requireMember(Long chatId, Long userId) {
        rooms.getById(chatId);
        members.findMembership(chatId, userId)
                .filter(m -> !m.hasLeft())
                .orElseThrow(ChatNotMemberException::new);
    }
}
