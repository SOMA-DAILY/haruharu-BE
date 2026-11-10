package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 모델 입력의 기록 한 줄(analysis-input.schema.json 의 history_records 항목과 같은 키).
 * source_id 는 chat:<메시지 id> 또는 video:<영상 id>, actor_id 는 사용자 id 문자열 또는 system 이다.
 */
public record HistoryRecord(
        String sourceId,
        String actorId,
        String kind,
        String role,
        String occurredAt,
        String availableAt,
        String revision,
        String text,
        List<String> parentSourceIds
) {
    public static final String KIND_CHAT = "chat";
    public static final String KIND_VIDEO = "video_description";
    public static final String ROLE_ROOM = "room";
    public static final String ROLE_OWN = "own";
    public static final String ROLE_INTERACTION = "interaction";
    public static final String ROLE_PARENT_CONTEXT = "parent_context";
    public static final String SYSTEM_ACTOR = "system";

    /** 실제 시각 순, 같은 시각이면 source_id 순(ISO 문자열 순서는 소수 초에서 틀린다). */
    public static final Comparator<HistoryRecord> CHRONOLOGICAL =
            Comparator.comparing((HistoryRecord record) -> Instant.parse(record.occurredAt()))
                    .thenComparing(HistoryRecord::sourceId);

    public HistoryRecord withRole(String newRole, List<String> parents) {
        return new HistoryRecord(sourceId, actorId, kind, newRole, occurredAt, availableAt, revision, text, parents);
    }

    public HistoryRecord withParents(List<String> parents) {
        return withRole(role, parents);
    }

    /** source_id 의 숫자 부분(메시지·영상 id). */
    public long sourceRefId() {
        return Long.parseLong(sourceId.substring(sourceId.indexOf(':') + 1));
    }
}
