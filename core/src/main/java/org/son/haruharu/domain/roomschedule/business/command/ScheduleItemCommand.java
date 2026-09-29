package org.son.haruharu.domain.roomschedule.business.command;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 편성 행 입력(#457). 추가(POST)와 방 생성의 chat_schedule[] 는 run_at 이 null 이면 각각 "지금" / 400.
 * 수정(PATCH)은 run_at·reveal_fields·push 가 null 이면 기존 값을 두고, 운영 글·푸시 제목·본문은 받은 값으로 바꾼다.
 */
public record ScheduleItemCommand(
        LocalDateTime runAt,
        List<String> revealFields,
        String messageContent,
        Boolean push,
        String pushTitle,
        String pushBody
) {}
