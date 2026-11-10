package org.son.haruharu.domain.replyrecommendation.implement;

/** 분석 요청 하나의 입력 스냅샷(저장 전). 원본 주소는 서명하지 않은 저장값이다. */
public record AnalysisInputDraft(
        AnalysisRequest request,
        String publishedAt,
        String caption,
        String mediaUrl,
        HistorySnapshot history
) {}
