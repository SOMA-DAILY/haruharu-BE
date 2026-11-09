package org.son.haruharu.domain.replyrecommendation.implement;

import java.util.List;

/** 권한·시점을 확인한 방 기록 묶음. 해시는 키를 정렬한 JSON 으로 만든다. */
public record HistorySnapshot(List<HistoryRecord> records, List<ExcludedSource> excluded, String hash, String status) {

    public static final String COMPLETE = "COMPLETE";
    public static final String NO_RECORDS = "NO_RECORDS";
    public static final String SOURCE_UNAVAILABLE = "SOURCE_UNAVAILABLE";

    public static HistorySnapshot of(List<HistoryRecord> records, List<ExcludedSource> excluded) {
        return new HistorySnapshot(List.copyOf(records), List.copyOf(excluded),
                AiReplyJson.sha256(AiReplyJson.write(records)), records.isEmpty() ? NO_RECORDS : COMPLETE);
    }

    /** 단체 채팅을 찾지 못하는 등 기록 원본을 읽을 수 없을 때. */
    public static HistorySnapshot unavailable() {
        return new HistorySnapshot(List.of(), List.of(), AiReplyJson.sha256("[]"), SOURCE_UNAVAILABLE);
    }
}
