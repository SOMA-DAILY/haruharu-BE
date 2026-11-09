package org.son.haruharu.domain.replyrecommendation.implement;

/** 스냅샷에서 뺀 기록과 이유(원문 없음). */
public record ExcludedSource(String sourceId, String reason) {

    public static final String ADMIN_DELETED = "ADMIN_DELETED";
    public static final String ACCESS_SCOPE = "ACCESS_SCOPE";
    public static final String NO_TEXT = "NO_TEXT";
}
