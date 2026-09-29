package org.son.haruharu.domain.roomschedule.implement;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 방 편성 한 줄(#457). 운영 글(message_content)과 공개 항목(reveal_fields) 중 하나 이상을 run_at 에 보낸다.
 * 시각 제한은 없다 — 방 시작 전 행은 방이 열린 뒤 나가고, 종료 후 행은 FAILED(ROOM_ENDED)로 남는다.
 */
public record RoomScheduleItem(
        Long id,
        Long roomId,
        LocalDateTime runAt,
        List<RevealField> revealFields,
        String messageContent,
        boolean push,
        String pushTitle,
        String pushBody,
        RoomScheduleStatus status,
        String failReason,
        Long sentMessageId,
        String createdBy,
        String updatedBy,
        LocalDateTime sentAt,
        LocalDateTime createdAt
) {
    public RoomScheduleItem {
        revealFields = revealFields == null ? List.of() : List.copyOf(revealFields);
    }

    public static RoomScheduleItem scheduled(Long roomId, ScheduleContent content, LocalDateTime runAt, String actor) {
        return new RoomScheduleItem(null, roomId, runAt, content.revealFields(), content.messageContent(),
                content.push(), content.pushTitle(), content.pushBody(), RoomScheduleStatus.SCHEDULED,
                null, null, actor, actor, null, null);
    }

    public boolean hasMessage() {
        return messageContent != null;
    }

    public boolean hasReveal() {
        return !revealFields.isEmpty();
    }
}
