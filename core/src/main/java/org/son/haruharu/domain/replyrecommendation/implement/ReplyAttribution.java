package org.son.haruharu.domain.replyrecommendation.implement;

/** 실제 답장을 어느 노출에 잇는가(명세 10장). DB 에는 소문자 값으로 남긴다. */
public enum ReplyAttribution {
    EXPLICIT("explicit"),
    INFERRED("inferred"),
    UNEXPOSED("unexposed"),
    SUPERSEDED("superseded");

    private final String value;

    ReplyAttribution(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
