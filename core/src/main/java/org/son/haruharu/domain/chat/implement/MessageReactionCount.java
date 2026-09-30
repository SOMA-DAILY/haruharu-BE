package org.son.haruharu.domain.chat.implement;

/** 메시지별·타입별 반응 수 한 행(배치 집계 결과). */
public record MessageReactionCount(Long messageId, String reactionType, long count) {}
