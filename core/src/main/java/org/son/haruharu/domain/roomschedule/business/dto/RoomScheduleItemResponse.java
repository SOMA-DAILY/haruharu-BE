package org.son.haruharu.domain.roomschedule.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.roomschedule.implement.RevealField;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 편성 행(#457). day = run_at 달력 날짜 − 방 시작 달력 날짜 + 1(방 시작 전이면 0 이하 그대로). */
public record RoomScheduleItemResponse(
        @Schema(description = "편성 행 id", example = "1") Long id,
        @Schema(description = "보낼 시각(KST)") LocalDateTime runAt,
        @Schema(description = "DAY(달력 날짜 기준, 방 시작 전이면 0 이하)", example = "1", nullable = true) Integer day,
        @Schema(description = "공개 항목(MBTI, INTO_NOW, AGE, DEPARTMENT)") List<String> revealFields,
        @Schema(description = "운영 글(없으면 null)", nullable = true) String messageContent,
        @Schema(description = "운영 글 푸시 여부") boolean push,
        @Schema(description = "알림 제목(비면 기본값으로 보냄)", nullable = true) String pushTitle,
        @Schema(description = "알림 본문(비면 기본값으로 보냄)", nullable = true) String pushBody,
        @Schema(description = "SCHEDULED, SENDING, SENT, CANCELED, FAILED", example = "SCHEDULED") String status,
        @Schema(description = "실패 사유(EXPIRED, ROOM_ENDED, STUCK 또는 예외 앞 200자)", nullable = true) String failReason,
        @Schema(description = "보낸 운영 글 메시지 id", nullable = true) Long sentMessageId,
        @Schema(description = "만든 사람", example = "admin:4") String createdBy,
        @Schema(description = "보낸 시각", nullable = true) LocalDateTime sentAt
) {
    public static RoomScheduleItemResponse of(RoomScheduleItem item, Room room) {
        return new RoomScheduleItemResponse(item.id(), item.runAt(), dayOf(item.runAt(), room),
                item.revealFields().stream().map(RevealField::name).toList(), item.messageContent(), item.push(),
                item.pushTitle(), item.pushBody(), item.status().name(), item.failReason(), item.sentMessageId(),
                item.createdBy(), item.sentAt());
    }

    /** Room.currentDayAt 은 시각 기준(24시간)이라 쓰지 않는다 — 09:00 시작 방의 DAY2 08:00 행이 DAY1 로 나온다. */
    public static Integer dayOf(LocalDateTime at, Room room) {
        LocalDateTime anchor = room.startsAt() != null ? room.startsAt() : room.startedAt();
        if (at == null || anchor == null) {
            return null;
        }
        LocalDate start = anchor.toLocalDate();
        return (int) ChronoUnit.DAYS.between(start, at.toLocalDate()) + 1;
    }
}
