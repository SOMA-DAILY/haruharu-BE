package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;
import org.son.haruharu.domain.chat.implement.exception.InvalidChatSenderException;
import org.son.haruharu.domain.chat.implement.exception.InvalidChatMissionReferenceException;

/** 채팅 메시지. createdAt이 전송 시각(BaseEntity). 실제 전송은 #60(STOMP)에서 처리한다. */
public record ChatMessage(
        Long id,
        Long chatRoomId,
        Long senderId,
        String content,
        LocalDateTime createdAt,
        Long videoPostId,
        String clientRequestId,
        // 답장(#456): 원문 id + 저장 시점의 원문 보낸 사람 표시 이름·앞 50자 스냅샷
        Long replyToMessageId,
        String replyPreviewSender,
        String replyPreviewContent,
        // 운영 글(#457): 종류, 편성 행, 운영자 삭제 시각, 삭제 방송 시각
        ChatMessageType messageType,
        Long scheduleItemId,
        LocalDateTime adminDeletedAt,
        LocalDateTime deleteBroadcastAt,
        ChatSenderType senderType,
        Long roomMissionId,
        Long missionAnnouncementId,
        ChatPhoto photo,
        boolean referenceSuppressed
) {
    /** 이전 생성자 호환. 원본 인용은 보관하고 응답에서만 숨기는 퇴장 표시를 별도로 둔다. */
    public ChatMessage(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime createdAt,
                       Long videoPostId, String clientRequestId, Long replyToMessageId, String replyPreviewSender,
                       String replyPreviewContent, ChatMessageType messageType, Long scheduleItemId,
                       LocalDateTime adminDeletedAt, LocalDateTime deleteBroadcastAt, ChatSenderType senderType,
                       Long roomMissionId, Long missionAnnouncementId, ChatPhoto photo) {
        this(id,chatRoomId,senderId,content,createdAt,videoPostId,clientRequestId,replyToMessageId,
             replyPreviewSender,replyPreviewContent,messageType,scheduleItemId,adminDeletedAt,
             deleteBroadcastAt,senderType,roomMissionId,missionAnnouncementId,photo,false);
    }

    public ChatMessage(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime createdAt,
                       Long videoPostId, String clientRequestId, Long replyToMessageId, String replyPreviewSender,
                       String replyPreviewContent, ChatMessageType messageType, Long scheduleItemId,
                       LocalDateTime adminDeletedAt, LocalDateTime deleteBroadcastAt, ChatSenderType senderType,
                       Long roomMissionId, Long missionAnnouncementId) {
        this(id, chatRoomId, senderId, content, createdAt, videoPostId, clientRequestId, replyToMessageId,
                replyPreviewSender, replyPreviewContent, messageType, scheduleItemId, adminDeletedAt,
                deleteBroadcastAt, senderType, roomMissionId, missionAnnouncementId, null);
    }

    public static ChatMessage photo(Long chatRoomId, Long senderId, String requestId, ChatPhoto photo) {
        return new ChatMessage(null, chatRoomId, senderId, "사진", null, null, requestId,
                null, null, null, ChatMessageType.IMAGE, null, null, null, ChatSenderType.USER, null, null, photo);
    }

    /** 운영자가 지운 메시지 대신 내려주는 문구(#457). */
    public static final String DELETED_CONTENT = "삭제된 메시지";

    public ChatMessage {
        if (senderType == null || (senderType == ChatSenderType.USER && senderId == null)
                || (senderType == ChatSenderType.SYSTEM && senderId != null)) {
            throw new InvalidChatSenderException();
        }
        if (messageType == null) {
            messageType = ChatMessageType.TEXT;
        }
        if ((messageType == ChatMessageType.IMAGE) != (photo != null)
                || (photo != null && (senderType != ChatSenderType.USER || clientRequestId == null))) {
            throw new org.son.haruharu.domain.chat.implement.exception.InvalidChatPhotoException();
        }
        if (messageType == ChatMessageType.MISSION) {
            if (senderType != ChatSenderType.SYSTEM || roomMissionId == null || roomMissionId <= 0
                    || missionAnnouncementId == null || missionAnnouncementId <= 0) {
                throw new InvalidChatMissionReferenceException();
            }
        } else if (roomMissionId != null || missionAnnouncementId != null) {
            throw new InvalidChatMissionReferenceException();
        }
    }

    public ChatMessage(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime createdAt,
                       Long videoPostId, String clientRequestId, Long replyToMessageId, String replyPreviewSender,
                       String replyPreviewContent, ChatMessageType messageType, Long scheduleItemId,
                       LocalDateTime adminDeletedAt, LocalDateTime deleteBroadcastAt, ChatSenderType senderType) {
        this(id, chatRoomId, senderId, content, createdAt, videoPostId, clientRequestId, replyToMessageId,
                replyPreviewSender, replyPreviewContent, messageType, scheduleItemId, adminDeletedAt,
                deleteBroadcastAt, senderType, null, null);
    }

    public ChatMessage(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime createdAt,
                       Long videoPostId, String clientRequestId, Long replyToMessageId, String replyPreviewSender,
                       String replyPreviewContent) {
        this(id, chatRoomId, senderId, content, createdAt, videoPostId, clientRequestId, replyToMessageId,
                replyPreviewSender, replyPreviewContent, ChatMessageType.TEXT, null, null, null, ChatSenderType.USER);
    }

    /** 답장 미리보기로 남기는 원문 앞부분 글자 수(codePoint). */
    public static final int REPLY_PREVIEW_LENGTH = 50;

    public ChatMessage(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime createdAt) {
        this(id, chatRoomId, senderId, content, createdAt, null, null);
    }

    public ChatMessage(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime createdAt,
                       Long videoPostId, String clientRequestId) {
        this(id, chatRoomId, senderId, content, createdAt, videoPostId, clientRequestId, null, null, null);
    }

    public static ChatMessage create(Long chatRoomId, Long senderId, String content) {
        return new ChatMessage(null, chatRoomId, senderId, content, null);
    }

    /** 특정 메시지에 답장(#456). 원문 보낸 사람 이름과 원문 앞 50자를 스냅샷으로 남긴다. */
    public static ChatMessage reply(Long chatRoomId, Long senderId, String content, ChatMessage original,
                                    String originalSenderName) {
        return new ChatMessage(null, chatRoomId, senderId, content, null, null, null,
                original.id(), originalSenderName, previewOf(original.content()));
    }

    /** 인가된 운영 요청의 실행 경로에서 생성하는 계정 없는 시스템 안내. */
    public static ChatMessage system(Long chatRoomId, String content, Long scheduleItemId) {
        return new ChatMessage(null, chatRoomId, null, content, null, null, null, null, null, null,
                ChatMessageType.TEXT, scheduleItemId, null, null, ChatSenderType.SYSTEM);
    }

    public static ChatMessage mission(Long chatRoomId, String content, Long roomMissionId, Long announcementId) {
        return new ChatMessage(null, chatRoomId, null, content, null, null, null, null, null, null,
                ChatMessageType.MISSION, null, null, null, ChatSenderType.SYSTEM, roomMissionId, announcementId);
    }

    public boolean isSystem() {
        return senderType == ChatSenderType.SYSTEM;
    }

    public boolean isOwnedBy(Long userId) {
        return senderType == ChatSenderType.USER && senderId.equals(userId);
    }

    public boolean isAdminDeleted() {
        return adminDeletedAt != null;
    }

    /** 화면에 보일 내용. 운영자가 지웠으면 '삭제된 메시지'. */
    public String displayContent() {
        return isAdminDeleted() ? DELETED_CONTENT : content;
    }

    /** 화면에 보일 발신자 이름. 운영 글만 '하루', 나머지는 null(앱이 members 에서 찾는다). */
    public String senderName() {
        return isSystem() ? ChatSenderType.SYSTEM_SENDER_NAME : null;
    }

    /** 단체 채팅에 저장되는 영상 답장. clientRequestId로 재시도를 멱등 처리한다. */
    public static ChatMessage videoReply(
            Long chatRoomId, Long senderId, String content, Long videoPostId, String clientRequestId) {
        return new ChatMessage(null, chatRoomId, senderId, content, null, videoPostId, clientRequestId);
    }

    static String previewOf(String content) {
        if (content == null) {
            return null;
        }
        int codePoints = content.codePointCount(0, content.length());
        if (codePoints <= REPLY_PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, content.offsetByCodePoints(0, REPLY_PREVIEW_LENGTH));
    }
}
