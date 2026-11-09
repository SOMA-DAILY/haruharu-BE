package org.son.haruharu.domain.replyrecommendation.implement;

/** 분석·개인화 입력에 들어간 원문 출처 하나(무효화 확인용). */
public record EvidenceRef(String sourceKind, long sourceRefId) {

    public boolean chat() {
        return HistoryRecord.KIND_CHAT.equals(sourceKind);
    }

    public boolean video() {
        return HistoryRecord.KIND_VIDEO.equals(sourceKind);
    }
}
