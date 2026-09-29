package org.son.haruharu.domain.roomschedule.business.command;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 편성 행 입력(#457). 추가(POST)와 방 생성의 chat_schedule[] 는 run_at 이 null 이면 각각 "지금" / 400.
 * 수정(PATCH)은 모든 칸이 null 이면 기존 값을 둔다. 운영 글·푸시 제목·본문을 지우려면 빈 문자열("")을 보낸다.
 */
public record ScheduleItemCommand(
        LocalDateTime runAt,
        List<String> revealFields,
        String messageContent,
        Boolean push,
        String pushTitle,
        String pushBody
) {}
