package org.son.haruharu.domain.video.implement;

/**
 * 영상 반응 유형의 단일 출처. 고정 이모지 세트(자유 이모지 아님)로, reactor당 1개(교체식)만 남긴다.
 * 저장·전달은 name() 문자열로 하되(DB엔 enum name), 검증·집계·이모지 노출은 이 enum을 기준으로 한다.
 */
public enum ReactionType {
    LIKE("👍"),
    HEART("❤️"),
    LOVE("😍"),
    LAUGH("😂"),
    WOW("😮"),
    FIRE("🔥");

    private final String emoji;

    ReactionType(String emoji) {
        this.emoji = emoji;
    }

    public String emoji() {
        return emoji;
    }

    public static ReactionType parse(String name) {
        for (ReactionType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }

    /** name() → 이모지. 알 수 없는 값(과거 유형 등)이면 null. 문자열 name으로 이모지를 역참조할 때 쓴다. */
    public static String emojiOf(String name) {
        ReactionType type = parse(name);
        return type == null ? null : type.emoji;
    }
}
