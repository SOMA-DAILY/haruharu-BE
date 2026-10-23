package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 답장 인용(#456). sender_name 과 preview 는 저장 시점 스냅샷이다.
 * 원문이 삭제됐거나 조회자가 원문 보낸 사람을 차단했으면 available=false, preview=null(앱은 "볼 수 없는 메시지").
 */
public record ReplyToView(
        @Schema(description = "원문 메시지 id", example = "880") Long messageId,
        @Schema(description = "원문 보낸 사람 표시 이름(GROUP 가명 / DIRECT 실명)", example = "선우") String senderName,
        @Schema(description = "원문 앞 50자(볼 수 없으면 null)", nullable = true) String preview,
        @Schema(description = "원문을 볼 수 있는지") boolean available,
        ChatPhotoView photo
) {
    public ReplyToView(Long messageId, String senderName, String preview, boolean available) {
        this(messageId, senderName, preview, available, null);
    }

    public ReplyToView withPhoto(ChatPhotoView photo) {
        return new ReplyToView(messageId, senderName, preview, available, available ? photo : null);
    }

    public static ReplyToView of(Long messageId, String senderName, String preview, boolean available) {
        return new ReplyToView(messageId, senderName, available ? preview : null, available);
    }
}
