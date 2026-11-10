package org.son.haruharu.domain.replyrecommendation.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthCollector;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthProperties;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthTarget;

@ExtendWith(MockitoExtension.class)
class ReplyDepthLabelServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock ReplyDepthCollector collector;

    private static ReplyDepthTarget target(long messageId) {
        return new ReplyDepthTarget(messageId, 77L, 10L, Instant.parse("2026-11-08T00:00:00Z"), null, null, null);
    }

    @Test
    @DisplayName("스위치가 꺼져 있으면 DB 를 보지 않는다(기존 동작 그대로)")
    void givenDisabled_thenNothingTouched() {
        var service = new ReplyDepthLabelService(new ReplyDepthProperties(false, 24, 100, 200, 3), collector, CLOCK);

        assertThat(service.collectDue()).isZero();

        verifyNoInteractions(collector);
    }

    @Test
    @DisplayName("한 답장이 실패해도 나머지 라벨을 계속 만든다")
    void givenOneFailure_thenOthersContinue() {
        var service = new ReplyDepthLabelService(new ReplyDepthProperties(true, 24, 100, 200, 3), collector, CLOCK);
        given(collector.ready()).willReturn(true);
        given(collector.findDue(CLOCK.instant())).willReturn(List.of(target(1), target(2), target(3)));
        given(collector.observe(any(), any())).willReturn(true);
        given(collector.observe(target(2), CLOCK.instant())).willThrow(new IllegalStateException("db"));

        assertThat(service.collectDue()).isEqualTo(2);

        then(collector).should().observe(target(3), CLOCK.instant());
    }

    @Test
    @DisplayName("같은 답장이 max-attempts 번 연속 실패하면 LABEL_FAILED 로 닫아 다음 배치 앞을 비운다")
    void givenRepeatedFailure_thenClosedAfterMaxAttempts() {
        var service = new ReplyDepthLabelService(new ReplyDepthProperties(true, 24, 100, 200, 3), collector, CLOCK);
        given(collector.ready()).willReturn(true);
        given(collector.findDue(CLOCK.instant())).willReturn(List.of(target(1)));
        given(collector.observe(target(1), CLOCK.instant())).willThrow(new IllegalStateException("bad data"));

        service.collectDue();
        service.collectDue();
        then(collector).should(never()).closeAsFailed(any(), any());

        service.collectDue();
        then(collector).should(times(1)).closeAsFailed(target(1), CLOCK.instant());
    }

    @Test
    @DisplayName("중간에 성공하면 실패 횟수를 다시 센다")
    void givenSuccessBetweenFailures_thenCountReset() {
        var service = new ReplyDepthLabelService(new ReplyDepthProperties(true, 24, 100, 200, 2), collector, CLOCK);
        given(collector.ready()).willReturn(true);
        given(collector.findDue(CLOCK.instant())).willReturn(List.of(target(1)));
        given(collector.observe(target(1), CLOCK.instant()))
                .willThrow(new IllegalStateException("transient"))
                .willReturn(false)
                .willThrow(new IllegalStateException("transient"));

        service.collectDue();
        service.collectDue();
        service.collectDue();

        then(collector).should(never()).closeAsFailed(any(), any());
    }
}
