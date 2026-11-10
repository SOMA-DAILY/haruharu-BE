package org.son.haruharu.domain.replyrecommendation.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplyDepthCollectorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime SENT = LocalDateTime.of(2026, 11, 8, 14, 0);
    private static final Instant SENT_AT = SENT.atZone(SEOUL).toInstant();

    @Mock ReplyDepthRepository repository;

    ReplyDepthCollector collector;

    @BeforeEach
    void setUp() {
        var recommendation = new ReplyRecommendationProperties(true, false, 10, 1000, Duration.ofMinutes(30),
                Duration.ofHours(24), new ReplyRecommendationProperties.Collection(false, List.of(10L)),
                new ReplyRecommendationProperties.Snapshot(3000, 20, 5, Duration.ofDays(30)), List.of());
        collector = new ReplyDepthCollector(repository, new ReplyDepthProperties(true, 24, 100, 200, 3),
                new RecommendationCollectionPolicy(recommendation), Clock.fixed(SENT_AT, SEOUL));
    }

    private static ReplyDepthTarget target(Long viewer, String provenance) {
        return new ReplyDepthTarget(500L, 77L, viewer, SENT_AT, "set-1", "c1", provenance);
    }

    private ReplyDepthObservation inserted() {
        ArgumentCaptor<ReplyDepthObservation> captor = ArgumentCaptor.forClass(ReplyDepthObservation.class);
        then(repository).should().insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("관찰 기간이 끝나면 기간 끝까지의 답글로 계산해 넣는다")
    void givenWindowEnded_thenObservedWithWindow() {
        Instant now = SENT_AT.plus(Duration.ofHours(25));
        given(repository.findRoot(500L)).willReturn(Optional.of(new ReplyDepthRoot(500L, 3L, SENT, false, 42L)));
        given(repository.findRepliesAfter(3L, 500L, SENT.plusHours(24))).willReturn(List.of(
                new ReplyDepthMessage(501L, 500L, false), new ReplyDepthMessage(502L, 501L, false),
                new ReplyDepthMessage(503L, 502L, false)));
        given(repository.insert(any())).willReturn(true);

        assertThat(collector.observe(target(10L, "QA"), now)).isTrue();

        ReplyDepthObservation row = inserted();
        assertThat(row.result()).isEqualTo(ReplyDepthResult.observed(3, 1));
        assertThat(row.roomId()).isEqualTo(3L);
        assertThat(row.windowStart()).isEqualTo(SENT_AT);
        assertThat(row.windowEnd()).isEqualTo(SENT_AT.plus(Duration.ofHours(24)));
        assertThat(row.observedAt()).isEqualTo(now);
        assertThat(row.provenance()).isEqualTo("QA");
        assertThat(row.sourceSetId()).isEqualTo("set-1");
        // 학습 분할용 기수는 답장 채팅방의 3일 방 기수를 그대로 남긴다.
        assertThat(row.cohortId()).isEqualTo(42L);
        // 작성자를 모르면 보조 지표는 비운다.
        assertThat(row.ownerReplied()).isNull();
        assertThat(row.followupMessageCount()).isNull();
    }

    @Test
    @DisplayName("관찰 기간이 끝나지 않았으면 계산하지 않는다")
    void givenWindowOpen_thenSkipped() {
        given(repository.findRoot(500L)).willReturn(Optional.of(new ReplyDepthRoot(500L, 3L, SENT, false, null)));

        assertThat(collector.observe(target(10L, null), SENT_AT.plus(Duration.ofHours(23)))).isFalse();

        then(repository).should(never()).findRepliesAfter(any(), any(), any());
        then(repository).should(never()).insert(any());
    }

    @Test
    @DisplayName("답장 메시지가 숨겨졌거나 없으면 미관측(depth null)으로 남긴다")
    void givenReplyHiddenOrMissing_thenUnobserved() {
        Instant now = SENT_AT.plus(Duration.ofHours(25));
        given(repository.findRoot(500L)).willReturn(Optional.empty());
        given(repository.insert(any())).willReturn(true);

        collector.observe(target(11L, null), now);

        ReplyDepthObservation row = inserted();
        assertThat(row.result()).isEqualTo(ReplyDepthResult.unobserved(ReplyDepthResult.REPLY_UNAVAILABLE));
        assertThat(row.roomId()).isNull();
        assertThat(row.cohortId()).isNull();
        assertThat(row.windowStart()).isEqualTo(SENT_AT);
        // 노출 없이 보낸 답장은 수집 정책으로 출처를 정한다(QA 목록에 없으면 실사용자).
        assertThat(row.provenance()).isEqualTo("REAL_USER");
        then(repository).should(never()).findRepliesAfter(any(), any(), any());
    }

    @Test
    @DisplayName("노출 출처가 모르는 값(SYNTHETIC 등)이어도 멈추지 않고 그대로 적는다")
    void givenUnknownEpisodeProvenance_thenCopiedAsIs() {
        Instant now = SENT_AT.plus(Duration.ofHours(25));
        given(repository.findRoot(500L)).willReturn(Optional.of(new ReplyDepthRoot(500L, 3L, SENT, false, null)));
        given(repository.findRepliesAfter(3L, 500L, SENT.plusHours(24))).willReturn(List.of());
        given(repository.insert(any())).willReturn(true);

        collector.observe(target(10L, "SYNTHETIC"), now);

        assertThat(inserted().provenance()).isEqualTo("SYNTHETIC");
    }

    @Test
    @DisplayName("실패로 닫을 때는 채팅을 다시 읽지 않고 보낸 시각 기준 기간의 LABEL_FAILED 미관측을 넣는다")
    void givenCloseAsFailed_thenUnobservedLabelFailed() {
        Instant now = SENT_AT.plus(Duration.ofHours(25));
        given(repository.insert(any())).willReturn(true);

        assertThat(collector.closeAsFailed(target(10L, null), now)).isTrue();

        ReplyDepthObservation row = inserted();
        assertThat(row.result()).isEqualTo(ReplyDepthResult.unobserved(ReplyDepthResult.LABEL_FAILED));
        assertThat(row.windowStart()).isEqualTo(SENT_AT);
        assertThat(row.windowEnd()).isEqualTo(SENT_AT.plus(Duration.ofHours(24)));
        assertThat(row.provenance()).isEqualTo("QA");
        assertThat(row.cohortId()).isNull();
        then(repository).should(never()).findRoot(any());
    }

    @Test
    @DisplayName("보조 지표: 작성자 답글 여부와 답장한 사람·작성자의 기간 안 메시지 수를 Depth 와 따로 남긴다")
    void givenOwnerKnown_thenAuxiliaryMetrics() {
        Instant now = SENT_AT.plus(Duration.ofHours(25));
        given(repository.findRoot(500L)).willReturn(Optional.of(new ReplyDepthRoot(500L, 3L, SENT, false, null)));
        given(repository.findRepliesAfter(3L, 500L, SENT.plusHours(24))).willReturn(List.of(
                new ReplyDepthMessage(501L, 500L, false, 20L)));
        given(repository.findVideoAuthor(77L)).willReturn(Optional.of(20L));
        given(repository.countMessagesAfter(3L, 500L, SENT.plusHours(24), Set.of(10L, 20L))).willReturn(5);
        given(repository.insert(any())).willReturn(true);

        collector.observe(target(10L, null), now);

        ReplyDepthObservation row = inserted();
        assertThat(row.result()).isEqualTo(ReplyDepthResult.observed(1, 1));
        assertThat(row.ownerReplied()).isTrue();
        assertThat(row.followupMessageCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("미관측이면 작성자 답글 여부는 null, 메시지 수는 연결과 무관하게 센다")
    void givenChainBroken_thenOwnerRepliedNullButFollowupCounted() {
        Instant now = SENT_AT.plus(Duration.ofHours(25));
        given(repository.findRoot(500L)).willReturn(Optional.of(new ReplyDepthRoot(500L, 3L, SENT, false, null)));
        given(repository.findRepliesAfter(3L, 500L, SENT.plusHours(24))).willReturn(List.of(
                new ReplyDepthMessage(501L, 500L, true, 20L), new ReplyDepthMessage(502L, 501L, false, 10L)));
        given(repository.findVideoAuthor(77L)).willReturn(Optional.of(10L));
        given(repository.countMessagesAfter(3L, 500L, SENT.plusHours(24), Set.of(10L))).willReturn(1);
        given(repository.insert(any())).willReturn(true);

        collector.observe(target(10L, null), now);

        ReplyDepthObservation row = inserted();
        assertThat(row.result().status()).isEqualTo(ReplyDepthStatus.UNOBSERVED);
        assertThat(row.ownerReplied()).isNull();
        assertThat(row.followupMessageCount()).isEqualTo(1);
    }
}
