package org.son.haruharu.admin.domain.room.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.roomschedule.business.command.ScheduleItemCommand;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 편성 행 요청(#457). 추가·수정·방 생성의 chat_schedule[] 가 같은 모양이다. 검증은 도메인(ScheduleContent)이 칸별로 한다
 * (error.fields: run_at, message_content, push_title, push_body, reveal_fields).
 */
public record ScheduleItemRequest(
        @Schema(description = "보낼 시각. 추가에서 null 이면 지금, 방 생성에선 필수", example = "2026-10-11T18:00:00", nullable = true)
        LocalDateTime runAt,
        @Schema(description = "공개 항목(MBTI, INTO_NOW, AGE, DEPARTMENT). 수정에서 null 이면 그대로", nullable = true)
        List<String> revealFields,
        @Schema(description = "운영 글(500자)", nullable = true) String messageContent,
        @Schema(description = "운영 글 푸시 여부. 수정에서 null 이면 그대로", nullable = true) Boolean push,
        @Schema(description = "알림 제목(15자, 비면 '📢 하루 공지')", nullable = true) String pushTitle,
        @Schema(description = "알림 본문(30자, 비면 '방 채팅에 새 공지가 올라왔어요')", nullable = true) String pushBody
) {
    public ScheduleItemCommand toCommand() {
        return new ScheduleItemCommand(runAt, revealFields, messageContent, push, pushTitle, pushBody);
    }
}
