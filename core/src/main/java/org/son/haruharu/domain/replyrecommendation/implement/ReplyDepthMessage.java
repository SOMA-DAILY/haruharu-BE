package org.son.haruharu.domain.replyrecommendation.implement;

/**
 * 관찰 기간 안의 답글 메시지(id·답글 대상·보낸 사람만, 본문 없음).
 *
 * @param excluded 운영자 삭제·삭제·퇴장 멤버로 숨겨진 메시지. 세지 않는다.
 * @param senderId 보낸 회원. 시스템 메시지면 null.
 */
public record ReplyDepthMessage(Long id, Long replyToMessageId, boolean excluded, Long senderId) {

    public ReplyDepthMessage(Long id, Long replyToMessageId, boolean excluded) {
        this(id, replyToMessageId, excluded, null);
    }
}
