package org.son.haruharu.domain.question.implement;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

class QuestionTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 13, 12, 0);

    private Question question(String content) {
        return Question.create(1L, 10L, 11L, 100L, 101L, "달님", content, UUID.randomUUID(), now);
    }

    @Test
    @DisplayName("질문은 공백을 제거하고 1~50자만 허용한다")
    void givenText_whenCreate_thenValidateLength() {
        assertThat(question(" 안녕 ").content()).isEqualTo("안녕");
        assertThatThrownBy(() -> question(" \n\t")).hasMessageContaining("질문");
        assertThatThrownBy(() -> question("가".repeat(51))).hasMessageContaining("질문");
        assertThatThrownBy(() -> question("\u00a0\u2007\u202f")).hasMessageContaining("질문");
        assertThat(question("😀".repeat(50)).content().codePointCount(0, 100)).isEqualTo(50);
        assertThat(question("가".repeat(50)).content()).hasSize(50);
    }

    @Test
    @DisplayName("답변은 100자 이내이고 재시도는 같은 답변만 허용한다")
    void givenQuestion_whenAnswer_thenImmutableOnce() {
        Question answered = question("안녕").answer(" 반가워 ", now);
        assertThat(answered.answer()).isEqualTo("반가워");
        assertThat(answered.answer("반가워", now)).isEqualTo(answered);
        assertThatThrownBy(() -> answered.answer("다른 답변", now)).hasMessageContaining("답변");
        assertThatThrownBy(() -> question("안녕").answer(" ", now)).hasMessageContaining("답변");
        assertThatThrownBy(() -> question("안녕").answer("가".repeat(101), now))
                .hasMessageContaining("답변");
    }

    @Test
    @DisplayName("읽음은 당사자별로 저장하며 신고는 신고자만 숨긴다")
    void givenQuestion_whenReadAndReport_thenKeepIndependentSides() {
        Question q = question("안녕");
        assertThat(q.unreadFor(101L)).isTrue();
        q = q.readBy(101L);
        assertThat(q.unreadFor(101L)).isFalse();
        q = q.answer("반가워", now).reportedBy(101L);
        assertThat(q.hiddenFor(101L)).isTrue();
        assertThat(q.hiddenFor(100L)).isFalse();
        assertThat(q.unreadFor(101L)).isFalse();
        assertThat(q.unreadFor(100L)).isTrue();
        assertThat(q.readBy(100L).unreadFor(100L)).isFalse();
    }
}
