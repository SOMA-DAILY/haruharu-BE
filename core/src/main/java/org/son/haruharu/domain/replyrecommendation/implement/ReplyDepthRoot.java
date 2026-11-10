package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.LocalDateTime;

/**
 * 답장 메시지 자체(채팅방·저장 시각). excluded 면 운영자 삭제·삭제·퇴장 멤버로 숨겨졌다.
 *
 * @param cohortId 채팅방의 3일 방 기수(rooms.cohort_id). 3일 방이 없거나 기수 없는 방이면 null.
 */
public record ReplyDepthRoot(Long messageId, Long roomId, LocalDateTime createdAt, boolean excluded, Long cohortId) {}
