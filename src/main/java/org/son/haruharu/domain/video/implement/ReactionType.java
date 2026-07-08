package org.son.haruharu.domain.video.implement;

/** 영상 반응 유형의 단일 출처. 저장·전달은 name() 문자열로 하되 검증·집계는 이 enum을 기준으로 한다. */
public enum ReactionType {
    LIKE, CURIOUS, EMPATHY;

    public static ReactionType parse(String name) {
        for (ReactionType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }
}
