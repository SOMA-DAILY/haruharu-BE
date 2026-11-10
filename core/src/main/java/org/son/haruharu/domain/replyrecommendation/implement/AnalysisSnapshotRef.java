package org.son.haruharu.domain.replyrecommendation.implement;

/** 작업기가 저장한 분석 결과의 요약(앱은 상태만 본다). */
public record AnalysisSnapshotRef(Long id, Long analysisInputId, Long videoId, String status) {

    public boolean ready() {
        return "READY".equals(status);
    }
}
