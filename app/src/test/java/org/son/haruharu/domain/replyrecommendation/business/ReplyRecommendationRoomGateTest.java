package org.son.haruharu.domain.replyrecommendation.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.business.event.VideoReplySavedEvent;
import org.son.haruharu.domain.replyrecommendation.implement.ActiveVersionReader;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisRequestRegistrar;
import org.son.haruharu.domain.replyrecommendation.implement.EnsureRateLimiter;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationAccessJudge;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationCollectionPolicy;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationEventRecorder;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationGuard;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationRequestStore;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationResolver;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyLinkRecorder;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyRecommendationProperties;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 운영은 운영진 시험 방에서만 먼저 켜고(개인정보처리방침 개정 전), 개정 뒤 방 목록을 비워 전체에 연다(2026-11-09 결정).
 * 시험 방이 아닌 방에서는 분석 요청·추천·사건·답장 연결이 모두 생기지 않아야 한다.
 */
class ReplyRecommendationRoomGateTest {

    private static final long TEST_ROOM = 75L;
    private static final long OTHER_ROOM = 12L;

    private static ReplyRecommendationProperties properties(boolean enabled, List<Long> roomIds) {
        return new ReplyRecommendationProperties(enabled, false, 10, 1000, Duration.ofMinutes(30), Duration.ofHours(24),
                new ReplyRecommendationProperties.Collection(true, List.of()),
                new ReplyRecommendationProperties.Snapshot(3000, 20, 5, Duration.ofDays(30)), roomIds);
    }

    @Test
    @DisplayName("방 목록이 비면 모든 방, 있으면 그 방에서만 켜진다. 기능이 꺼져 있으면 어느 방도 아니다")
    void givenRoomList_whenAsked_thenOnlyListedRoomsOrAll() {
        assertThat(properties(true, List.of()).enabledFor(OTHER_ROOM)).isTrue();
        assertThat(properties(true, List.of(TEST_ROOM)).enabledFor(TEST_ROOM)).isTrue();
        assertThat(properties(true, List.of(TEST_ROOM)).enabledFor(OTHER_ROOM)).isFalse();
        assertThat(properties(false, List.of(TEST_ROOM)).enabledFor(TEST_ROOM)).isFalse();
        assertThat(properties(false, List.of()).enabledFor(OTHER_ROOM)).isFalse();
    }

    @Test
    @DisplayName("시험 방이 아닌 방에 영상을 올리면 분석 요청을 넣지 않는다")
    void givenOtherRoom_whenVideoUploaded_thenNoAnalysisRequest() {
        AnalysisRequestRegistrar registrar = mock(AnalysisRequestRegistrar.class);
        ReplyRecommendationPublishListener listener = new ReplyRecommendationPublishListener(
                properties(true, List.of(TEST_ROOM)), registrar, Clock.systemUTC());

        listener.onVideoUploaded(new VideoUploadedEvent(1L, OTHER_ROOM, 2L, LocalDateTime.now()));
        verifyNoInteractions(registrar);

        listener.onVideoUploaded(new VideoUploadedEvent(3L, TEST_ROOM, 4L, LocalDateTime.now()));
        verify(registrar).registerPublished(eq(3L), eq(4L), any(Instant.class));
    }

    @Test
    @DisplayName("시험 방이 아닌 방의 추천 요청·조회는 권한 확인 전에 FEATURE_DISABLED 로 숨긴다")
    void givenOtherRoom_whenEnsureOrGet_thenFeatureDisabled() {
        RecommendationAccessJudge judge = mock(RecommendationAccessJudge.class);
        ReplyRecommendationService service = new ReplyRecommendationService(properties(true, List.of(TEST_ROOM)), judge,
                mock(ActiveVersionReader.class), mock(AnalysisRequestRegistrar.class),
                mock(RecommendationRequestStore.class), mock(RecommendationGuard.class),
                mock(RecommendationResolver.class), mock(EnsureRateLimiter.class), Clock.systemUTC());

        assertThat(service.ensure(OTHER_ROOM, 1L, 2L).reasonCode()).isEqualTo("FEATURE_DISABLED");
        assertThat(service.get(OTHER_ROOM, 1L, 2L).reasonCode()).isEqualTo("FEATURE_DISABLED");
        verifyNoInteractions(judge);
    }

    @Test
    @DisplayName("시험 방이 아닌 방의 노출 사건과 영상 답장은 남기지 않는다")
    void givenOtherRoom_whenEventOrReply_thenNothingRecorded() {
        RecommendationAccessJudge judge = mock(RecommendationAccessJudge.class);
        RecommendationEventRecorder events = mock(RecommendationEventRecorder.class);
        RecommendationCollectionPolicy collection = mock(RecommendationCollectionPolicy.class);
        ReplyRecommendationEventService eventService = new ReplyRecommendationEventService(
                properties(true, List.of(TEST_ROOM)), judge, mock(RecommendationRequestStore.class), events, collection,
                Clock.systemUTC());
        eventService.record(OTHER_ROOM, 1L, 2L, null);
        verifyNoInteractions(judge, events, collection);

        ReplyLinkRecorder links = mock(ReplyLinkRecorder.class);
        ReplyRecommendationReplyListener replies = new ReplyRecommendationReplyListener(
                properties(true, List.of(TEST_ROOM)), links, collection, mock(PlatformTransactionManager.class),
                Clock.systemUTC());
        replies.onReplySaved(new VideoReplySavedEvent(9L, 8L, OTHER_ROOM, 1L, 2L, LocalDateTime.now(), null, null, null));
        verifyNoInteractions(links, collection);
    }
}
