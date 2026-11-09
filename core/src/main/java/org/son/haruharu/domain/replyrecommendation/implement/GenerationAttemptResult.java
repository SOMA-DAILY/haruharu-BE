package org.son.haruharu.domain.replyrecommendation.implement;

/** 생성 시도 결과(작업기). 개인화 시도는 채택이 없으면 일반 세트 id 를 결과로 둔다. */
public record GenerationAttemptResult(String status, String failureCode, String resultSetId) {

    public boolean succeeded() {
        return "SUCCEEDED".equals(status) && resultSetId != null;
    }
}
