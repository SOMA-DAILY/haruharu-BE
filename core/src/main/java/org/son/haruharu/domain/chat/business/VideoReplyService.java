package org.son.haruharu.domain.chat.business;

import lombok.RequiredArgsConstructor;

import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.dto.VideoReplyPreview;
import org.son.haruharu.domain.chat.business.event.ChatMessageBroadcastEvent;
import org.son.haruharu.domain.chat.business.event.VideoReplySentEvent;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerBlockedException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerWithdrawnException;
import org.son.haruharu.domain.chat.business.exception.VideoReplyUnavailableException;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatMessageWriter;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.chat.implement.VideoReply;
import org.son.haruharu.domain.chat.implement.VideoReplyState;
import org.son.haruharu.domain.chat.implement.VideoReplyTarget;
import org.son.haruharu.domain.chat.implement.VideoReplyTargetReader;
import org.son.haruharu.domain.chat.implement.exception.VideoReplyInvalidException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

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
    private final VideoReplyTargetReader targets;
    private final S3StoragePort storage;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * 방의 단체 채팅에 영상 답장을 보내고, 영상 작성자에게만 알림을 보낸다
     *
     * <p>같은 requestId로 다시 요청하면 새로 저장하지 않고 처음 저장한 메시지를 돌려준다.
     * 이미 쓴 requestId에 내용이나 영상이 다른 요청이 오면 거부한다.
     * 진행 중(ACTIVE)이거나 최종 선택 중(SELECTING)인 방에서만 보낼 수 있다.
     *
     * @throws ChatNotMemberException 보낸 사람이 단체 채팅 멤버가 아닐 때
     * @throws VideoPostNotFoundException 영상이 삭제·숨김 상태이거나 다른 방의 영상일 때
     * @throws VideoReplyUnavailableException 영상 작성자가 단체 채팅을 나갔을 때
     */
    @Transactional
    public ChatMessageBroadcast send(
            Long roomId, Long videoId, Long senderId, String content, String requestId) {
        VideoReply reply = VideoReply.of(videoId, content, requestId);

        // 방에 속한 단체 채팅방을 찾고 잠근다
        ChatRoom chat = rooms.getByRoomId(roomId);
        rooms.lockForUpdate(chat.id());
        requireMember(chat.id(), senderId);

        // 재시도 확인
        Optional<ChatMessage> previous = messages.findByRequest(chat.id(), senderId, reply.requestId());
        if (previous.isPresent()) {
            if (!reply.matches(previous.get()))
                throw new VideoReplyInvalidException("이미 사용한 전송 요청입니다.");
            return ChatMessageBroadcast.from(previous.get());
        }

        // 방 상태 검증 : 진행 중이거나 최종 선택 중일때만 답장 가능
        Room room = sourceRooms.findById(roomId).orElseThrow(RoomNotFoundException::new);
        VideoReply.validateRoomOpen(room.statusAt(LocalDateTime.now(clock)));

        // 영상 및 작성자 검증
        VideoReplyTarget target = targets.get(roomId, chat.id(), videoId, senderId);
        requireReplyable(target, senderId);

        RoomMember sender = roomMembers.getByRoomIdAndUserId(roomId, senderId);
        ChatMessage saved = writer.save(reply.toMessage(chat.id(), senderId));

        ChatMessageBroadcast broadcast = ChatMessageBroadcast.from(saved);
        events.publishEvent(new ChatMessageBroadcastEvent(broadcast));

        // 이벤트 발생
        events.publishEvent(
                new VideoReplySentEvent(
                        chat.id(), target.owner().userId(), senderId, sender.displayPseudonym()));
        return broadcast;
    }

    /**
     * 채팅 화면에서 영상 답장 메시지에 붙은 영상 미리보기를 조회한다.
     *
     * <p>채팅 메시지에는 videoPostId만 있어서, 말풍선을 그릴 때 이 API로 영상 정보를 따로 가져온다.
     * 영상이 삭제·숨김되었거나 볼 수 없으면 예외 대신 DELETED/UNAVAILABLE 상태를 돌려준다.
     * 메시지는 남아 있으니, 화면이 "삭제된 영상입니다" 같은 자리 표시를 그릴 수 있게 하려는 것이다.
     */
    public VideoReplyPreview previewForChat(Long chatId, Long videoId, Long userId) {
        ChatRoom chat = rooms.getById(chatId);
        requireMember(chat.id(), userId);
        if (chat.type() != ChatRoomType.GROUP)
            throw new VideoReplyInvalidException("단체 채팅에서만 볼 수 있어요.");
        return preview(chat, videoId, userId);
    }

    /**
     * 방의 영상 화면에서 답장을 쓰기 전에 대상 영상 미리보기를 조회한다.
     *
     * <p>previewForChat과 판정·응답이 같고, 진입점만 chatId 대신 roomId다.
     */
    public VideoReplyPreview preview(Long roomId, Long videoId, Long userId) {
        ChatRoom chat = rooms.getByRoomId(roomId);
        requireMember(chat.id(), userId);
        return preview(chat, videoId, userId);
    }

    /** 두 미리보기 API의 공통 본문. 볼 수 있는 영상일 때만 미디어 URL을 서명한다. */
    private VideoReplyPreview preview(ChatRoom chat, Long videoId, Long userId) {
        VideoReplyTarget target = targets.get(chat.roomId(), chat.id(), videoId, userId);
        if (target.state() != VideoReplyState.AVAILABLE)
            return VideoReplyPreview.unavailable(videoId, target.state());
        return new VideoReplyPreview(
                videoId,
                VideoReplyState.AVAILABLE,
                storage.createPresignedGetUrl(target.post().videoUrl()),
                storage.createPresignedGetUrl(target.post().thumbnailUrl()),
                target.post().caption(),
                target.owner().displayPseudonym());
    }

    private void requireReplyable(VideoReplyTarget target, Long senderId) {
        if (target.isHiddenOrDeleted()) throw new VideoPostNotFoundException();
        if (target.isOwnedBy(senderId))
            throw new VideoReplyInvalidException("본인 영상에는 답장할 수 없어요.");
        switch (target.reason()) {
            case BLOCKED -> throw new ChatPartnerBlockedException();
            case OWNER_WITHDRAWN -> throw new ChatPartnerWithdrawnException();
            case OWNER_LEFT -> throw new VideoReplyUnavailableException();
            default -> {}
        }
    }

    private void requireMember(Long chatId, Long userId) {
        members.findActiveMembership(chatId, userId).orElseThrow(ChatNotMemberException::new);
    }
}
