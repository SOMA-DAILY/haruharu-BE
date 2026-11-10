package org.son.haruharu.domain.replyrecommendation.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;

@ExtendWith(MockitoExtension.class)
class ReplyLinkRecorderTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime SENT = LocalDateTime.of(2026, 11, 8, 14, 0);
    private static final Instant SENT_AT = SENT.atZone(SEOUL).toInstant();
    private static final String SESSION = "00000000-0000-4000-8000-0000000000aa";

    @Mock RecommendationFeedbackRepository feedback;
    @Mock RecommendationRepository recommendations;
    @Mock ChatMessageReader chatMessages;

    ReplyLinkRecorder recorder;

    @BeforeEach
    void setUp() {
        var properties = new ReplyRecommendationProperties(true, false, 10, 1000, Duration.ofMinutes(30),
                Duration.ofHours(24), new ReplyRecommendationProperties.Collection(false, List.of(10L)),
                new ReplyRecommendationProperties.Snapshot(3000, 20, 5, Duration.ofDays(30)), List.of());
        recorder = new ReplyLinkRecorder(feedback, recommendations, new RecommendationCollectionPolicy(properties),
                chatMessages, Clock.fixed(SENT_AT, SEOUL));
    }

    private static SavedVideoReply reply(Long viewer, String setId, String cardId, String session) {
        return new SavedVideoReply(500L, viewer, 77L, SENT, setId, cardId, session);
    }

    private static RecommendationSet set(Long videoId) {
        return new RecommendationSet("set-1", "GENERIC", 900L, videoId, null, "v1", 1, "READY",
                List.of(new RecommendationCard("c1", "question", "t")), SENT_AT.minusSeconds(60),
                SENT_AT.plusSeconds(3600), null);
    }

    private static ReplyEpisode episode(long id, String session, String setId, Instant last) {
        return new ReplyEpisode(id, session, setId, 10L, 77L, last.minusSeconds(10), last, Provenance.QA);
    }

    private ReplyLink savedLink() {
        var captor = ArgumentCaptor.forClass(ReplyLink.class);
        then(feedback).should().insertReplyLink(captor.capture(), any());
        return captor.getValue();
    }

    @Test
    @DisplayName("본인이 받은 세트의 출처면 그 노출에 explicit 로 잇고 답장 원문을 판정용으로 남긴다")
    void givenValidSource_whenRecord_thenExplicitOnMatchingEpisode() {
        given(recommendations.findSet("set-1")).willReturn(Optional.of(set(77L)));
        given(recommendations.viewerReceivedSet(10L, 77L, "set-1")).willReturn(true);
        var target = episode(1, SESSION, "set-1", SENT_AT.minusSeconds(30));
        var other = episode(2, "00000000-0000-4000-8000-0000000000bb", "set-1", SENT_AT.minusSeconds(5));
        given(feedback.findOpenEpisodes(10L, 77L, SENT_AT.minus(Duration.ofHours(24)), SENT_AT))
                .willReturn(List.of(target, other));
        given(chatMessages.findById(500L)).willReturn(Optional.of(new ChatMessage(500L, 3L, 10L, "어느 팀 응원해요?", SENT)));

        recorder.record(reply(10L, "set-1", "c1", SESSION), SENT_AT);

        then(feedback).should().closeEpisode(1L, 500L, SENT_AT, ReplyAttribution.EXPLICIT, SENT_AT);
        then(feedback).should().closeEpisode(2L, 500L, SENT_AT, ReplyAttribution.SUPERSEDED, SENT_AT);
        var link = savedLink();
        assertThat(link.sourceStatus()).isEqualTo(ReplySourceStatus.VALID);
        assertThat(link.attribution()).isEqualTo(ReplyAttribution.EXPLICIT);
        assertThat(link.episodeId()).isEqualTo(1L);
        then(feedback).should().saveReplySnapshot(1L, 500L, "어느 팀 응원해요?", Provenance.QA, SENT_AT);
    }

    @Test
    @DisplayName("다른 사용자의 세트로 보낸 답장은 연결만 무효로 하고 마지막 노출에 inferred 로 잇는다(AUTH02)")
    void givenOtherViewersSet_whenRecord_thenInvalidSourceAndInferred() {
        given(recommendations.findSet("set-1")).willReturn(Optional.of(set(77L)));
        given(recommendations.viewerReceivedSet(10L, 77L, "set-1")).willReturn(false);
        var latest = episode(3, SESSION, "set-9", SENT_AT.minusSeconds(5));
        given(feedback.findOpenEpisodes(eq(10L), eq(77L), any(), any()))
                .willReturn(List.of(episode(2, SESSION, "set-8", SENT_AT.minusSeconds(50)), latest));
        given(chatMessages.findById(500L)).willReturn(Optional.empty());

        recorder.record(reply(10L, "set-1", null, SESSION), SENT_AT);

        var link = savedLink();
        assertThat(link.sourceStatus()).isEqualTo(ReplySourceStatus.INVALID);
        assertThat(link.invalidReason()).isEqualTo("OTHER_VIEWER");
        assertThat(link.attribution()).isEqualTo(ReplyAttribution.INFERRED);
        assertThat(link.episodeId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("다른 영상의 세트·세트에 없는 카드는 무효 이유를 남긴다")
    void givenMismatchedSource_whenCheck_thenReasons() {
        given(recommendations.findSet("set-1")).willReturn(Optional.of(set(78L)));
        assertThat(recorder.check(reply(10L, "set-1", null, SESSION), 10L, 77L).reason()).isEqualTo("VIDEO_MISMATCH");

        given(recommendations.findSet("set-2")).willReturn(Optional.of(set(77L)));
        given(recommendations.viewerReceivedSet(10L, 77L, "set-2")).willReturn(true);
        assertThat(recorder.check(reply(10L, "set-2", "c9", SESSION), 10L, 77L).reason()).isEqualTo("CARD_NOT_IN_SET");
    }

    @Test
    @DisplayName("노출 전에 보낸 답장은 unexposed 다(노출 효과로 세지 않는다, FB03)")
    void givenNoOpenEpisode_whenRecord_thenUnexposed() {
        given(feedback.findOpenEpisodes(eq(10L), eq(77L), any(), any())).willReturn(List.of());

        recorder.record(reply(10L, null, null, null), SENT_AT);

        var link = savedLink();
        assertThat(link.sourceStatus()).isEqualTo(ReplySourceStatus.NONE);
        assertThat(link.attribution()).isEqualTo(ReplyAttribution.UNEXPOSED);
        then(feedback).should(never()).saveReplySnapshot(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("실사용자 수집이 꺼져 있으면 QA 계정이 아닌 사람의 답장은 남기지 않는다")
    void givenRealUserCollectionOff_whenRecord_thenStoresNothing() {
        recorder.record(reply(99L, "set-1", "c1", SESSION), SENT_AT);

        then(feedback).shouldHaveNoInteractions();
        then(recommendations).shouldHaveNoInteractions();
    }
}
