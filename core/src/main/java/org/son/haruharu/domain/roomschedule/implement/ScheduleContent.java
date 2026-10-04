package org.son.haruharu.domain.roomschedule.implement;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.roomschedule.implement.exception.InvalidScheduleItemException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 편성 행의 내용(공개 항목, 운영 글, 푸시). 검증은 두 가지뿐(#457): 운영 글이나 공개 항목 중 하나는 있어야 하고,
 * 글자 수(운영 글 500, 푸시 제목 15, 본문 30 — 이모지 한 글자, codePoint). 시각 검증은 하지 않는다.
 */
public record ScheduleContent(
        List<RevealField> revealFields,
        String messageContent,
        boolean push,
        String pushTitle,
        String pushBody
) {
    public static final int MESSAGE_MAX_LENGTH = 500;
    public static final int PUSH_TITLE_MAX_LENGTH = 15;
    public static final int PUSH_BODY_MAX_LENGTH = 30;

    /**
     * 요청값을 정규화(빈 문자열 → null, 공개 항목 중복 제거·표시 순서)하고 검증한다.
     * fieldPrefix 는 방 생성 요청처럼 여러 줄일 때 `chat_schedule[2].` 같은 칸 이름 앞부분.
     */
    public static ScheduleContent validated(List<String> revealFields, String messageContent, Boolean push,
                                            String pushTitle, String pushBody, String fieldPrefix) {
        List<ApiFieldError> errors = new ArrayList<>();
        Set<RevealField> fields = EnumSet.noneOf(RevealField.class);
        if (revealFields != null) {
            for (String raw : revealFields) {
                Optional<RevealField> parsed = RevealField.parse(raw);
                if (parsed.isEmpty()) {
                    errors.add(new ApiFieldError(fieldPrefix + "reveal_fields", "공개 항목을 다시 골라주세요"));
                    break;
                }
                fields.add(parsed.get());
            }
        }
        String content = blankToNull(messageContent);
        String title = blankToNull(pushTitle);
        String body = blankToNull(pushBody);
        if (content == null && fields.isEmpty()) {
            errors.add(new ApiFieldError(fieldPrefix + "message_content", "운영 글이나 공개 항목 중 하나는 있어야 해요"));
        }
        if (content != null && length(content) > MESSAGE_MAX_LENGTH) {
            errors.add(new ApiFieldError(fieldPrefix + "message_content", "500자까지 쓸 수 있어요"));
        }
        if (title != null && length(title) > PUSH_TITLE_MAX_LENGTH) {
            errors.add(new ApiFieldError(fieldPrefix + "push_title", "15자까지 쓸 수 있어요"));
        }
        if (body != null && length(body) > PUSH_BODY_MAX_LENGTH) {
            errors.add(new ApiFieldError(fieldPrefix + "push_body", "30자까지 쓸 수 있어요"));
        }
        if (!errors.isEmpty()) {
            throw new InvalidScheduleItemException(errors);
        }
        return new ScheduleContent(List.copyOf(fields), content, Boolean.TRUE.equals(push), title, body);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
