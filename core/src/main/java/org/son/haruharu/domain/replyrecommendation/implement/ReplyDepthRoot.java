package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.LocalDateTime;

/** 답장 메시지 자체(채팅방·저장 시각). excluded 면 운영자 삭제·삭제·퇴장 멤버로 숨겨졌다. */
public record ReplyDepthRoot(Long messageId, Long roomId, LocalDateTime createdAt, boolean excluded) {}
