package org.son.haruharu.domain.chat.business.event;

import java.time.LocalDateTime;

/**
 * 새 영상 답장이 저장됐다(재전송으로 기존 메시지를 돌려준 경우는 내지 않는다).
 * 답장 추천(#558)이 커밋 뒤에 실제 답장과 노출 기록을 잇는 데 쓴다. 답장 본문은 담지 않는다.
 * 추천 출처 세 칸은 앱이 보낸 값 그대로다(검증 전, 없으면 null).
 */
public record VideoReplySavedEvent(
        Long messageId,
        Long chatRoomId,
        Long roomId,
        Long videoPostId,
        Long senderId,
        LocalDateTime sentAt,
        String sourceSetId,
        String sourceCardId,
        String sourceSessionId
) {}
