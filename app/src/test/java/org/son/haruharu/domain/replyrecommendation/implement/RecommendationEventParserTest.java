package org.son.haruharu.domain.replyrecommendation.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.replyrecommendation.implement.exception.InvalidRecommendationEventException;

class RecommendationEventParserTest {

    private static final Instant NOW = Instant.parse("2026-11-08T04:00:00Z");
    private static final String EVENT = "00000000-0000-4000-8000-000000000001";
    private static final String SESSION = "00000000-0000-4000-8000-000000000002";

    private static RawRecommendationEvent raw(String type, String cardId, String occurredAt) {
        return new RawRecommendationEvent(EVENT, SESSION, "rq-1", "set-1", cardId, type, occurredAt);
    }

    @Test
    @DisplayName("같은 내용은 같은 payload 해시를 갖는다(재전송 204 판단)")
    void givenSameEvent_whenParseTwice_thenSamePayloadSha() {
        var first = RecommendationEventParser.parse(raw("impression", null, "2026-11-08T03:59:00Z"), 1L, 2L, Provenance.QA, NOW);
        var again = RecommendationEventParser.parse(raw("impression", null, "2026-11-08T12:59:00+09:00"), 1L, 2L, Provenance.QA, NOW);
        var different = RecommendationEventParser.parse(raw("impression", null, "2026-11-08T03:58:00Z"), 1L, 2L, Provenance.QA, NOW);

        assertThat(first.payloadSha()).isEqualTo(again.payloadSha());
        assertThat(first.payloadSha()).isNotEqualTo(different.payloadSha());
        assertThat(first.late()).isFalse();
    }

    @Test
    @DisplayName("card_selected 는 card_id 가 있어야 한다")
    void givenSelectedWithoutCard_whenParse_thenRejects() {
        assertThatThrownBy(() -> RecommendationEventParser.parse(raw("card_selected", null, "2026-11-08T03:59:00Z"),
                1L, 2L, Provenance.QA, NOW)).isInstanceOf(InvalidRecommendationEventException.class);
    }

    @Test
    @DisplayName("받은 시각보다 5분 넘게 미래인 사건은 거부하고, 15분 넘게 늦은 사건은 late 로 받는다")
    void givenClockSkew_whenParse_thenRejectsFutureAndFlagsLate() {
        assertThatThrownBy(() -> RecommendationEventParser.parse(raw("impression", null, "2026-11-08T04:05:01Z"),
                1L, 2L, Provenance.QA, NOW)).isInstanceOf(InvalidRecommendationEventException.class);
        var late = RecommendationEventParser.parse(raw("impression", null, "2026-11-08T03:30:00Z"), 1L, 2L, Provenance.QA, NOW);
        assertThat(late.late()).isTrue();
    }

    @Test
    @DisplayName("알 수 없는 유형·잘못된 id 형식은 거부한다")
    void givenMalformed_whenParse_thenRejects() {
        assertThatThrownBy(() -> RecommendationEventParser.parse(raw("typed", null, "2026-11-08T03:59:00Z"),
                1L, 2L, Provenance.QA, NOW)).isInstanceOf(InvalidRecommendationEventException.class);
        assertThatThrownBy(() -> RecommendationEventParser.parse(
                new RawRecommendationEvent("not-a-uuid", SESSION, "rq", "set", null, "impression", "2026-11-08T03:59:00Z"),
                1L, 2L, Provenance.QA, NOW)).isInstanceOf(InvalidRecommendationEventException.class);
    }
}
