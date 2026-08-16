package org.son.haruharu.domain.video.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReactionTypeTest {

    @Test
    @DisplayName("고정 이모지 세트는 6종이며 각 타입에 이모지가 매핑된다")
    void givenReactionType_whenInspect_thenSixEmojiMapped() {
        assertThat(ReactionType.values()).hasSize(6);
        assertThat(ReactionType.LIKE.emoji()).isEqualTo("👍");
        assertThat(ReactionType.HEART.emoji()).isEqualTo("❤️");
        assertThat(ReactionType.LOVE.emoji()).isEqualTo("😍");
        assertThat(ReactionType.LAUGH.emoji()).isEqualTo("😂");
        assertThat(ReactionType.WOW.emoji()).isEqualTo("😮");
        assertThat(ReactionType.FIRE.emoji()).isEqualTo("🔥");
    }

    @Test
    @DisplayName("parse는 name과 일치하면 enum을, 아니면 null을 반환한다")
    void givenName_whenParse_thenEnumOrNull() {
        assertThat(ReactionType.parse("FIRE")).isEqualTo(ReactionType.FIRE);
        assertThat(ReactionType.parse("CURIOUS")).isNull(); // 폐기된 구 유형
        assertThat(ReactionType.parse("ANGRY")).isNull();
    }

    @Test
    @DisplayName("emojiOf는 알려진 name이면 이모지를, 모르면 null을 반환한다")
    void givenName_whenEmojiOf_thenEmojiOrNull() {
        assertThat(ReactionType.emojiOf("LOVE")).isEqualTo("😍");
        assertThat(ReactionType.emojiOf("EMPATHY")).isNull(); // 폐기된 구 유형
    }
}
