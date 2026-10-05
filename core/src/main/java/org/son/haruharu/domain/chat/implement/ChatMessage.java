package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

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
        // 운영자 미션 카드(#474). 운영 글(ADMIN) 가운데 미션 카드일 때만 값이 있다. 삭제 표시는 #457 과 같은 adminDeletedAt.
        ChatMission mission
) {
    /** 운영자가 지운 메시지 대신 내려주는 문구(#457). */
    public static final String DELETED_CONTENT = "삭제된 메시지";

    public ChatMessage {
        if (messageType == null) {
            messageType = ChatMessageType.TEXT;
        }
    }

    public ChatMessage(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime createdAt,
                       Long videoPostId, String clientRequestId, Long replyToMessageId, String replyPreviewSender,
                       String replyPreviewContent, ChatMessageType messageType, Long scheduleItemId,
                       LocalDateTime adminDeletedAt, LocalDateTime deleteBroadcastAt) {
        this(id, chatRoomId, senderId, content, createdAt, videoPostId, clientRequestId, replyToMessageId,
                replyPreviewSender, replyPreviewContent, messageType, scheduleItemId, adminDeletedAt,
                deleteBroadcastAt, null);
    }

    public ChatMessage(Long id, Long chatRoomId, Long senderId, String content, LocalDateTime createdAt,
                       Long videoPostId, String clientRequestId, Long replyToMessageId, String replyPreviewSender,
                       String replyPreviewContent) {
        this(id, chatRoomId, senderId, content, createdAt, videoPostId, clientRequestId, replyToMessageId,
                replyPreviewSender, replyPreviewContent, ChatMessageType.TEXT, null, null, null);
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

    /** 운영 글(#457). 발신자는 운영 전용 시스템 유저(채팅방 멤버 아님). */
    public static ChatMessage admin(Long chatRoomId, Long systemUserId, String content, Long scheduleItemId) {
        return new ChatMessage(null, chatRoomId, systemUserId, content, null, null, null, null, null, null,
                ChatMessageType.ADMIN, scheduleItemId, null, null);
    }

    /** 운영자 미션 카드(#474). 운영 글(ADMIN)과 같은 발신자·삭제 규칙에 카드 메타데이터만 얹는다. */
    public static ChatMessage mission(Long chatRoomId, Long systemUserId, String content, ChatMission mission) {
        return new ChatMessage(null, chatRoomId, systemUserId, content, null, null, null, null, null, null,
                ChatMessageType.ADMIN, null, null, null, mission);
    }

    public boolean isAdmin() {
        return messageType == ChatMessageType.ADMIN;
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
        return isAdmin() ? ChatMessageType.SYSTEM_SENDER_NAME : null;
    }

    /** 화면에 보일 미션 카드. 운영자가 지웠으면 사진·촬영 버튼도 함께 숨긴다(#474). */
    public ChatMission visibleMission() {
        return isAdminDeleted() ? null : mission;
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
