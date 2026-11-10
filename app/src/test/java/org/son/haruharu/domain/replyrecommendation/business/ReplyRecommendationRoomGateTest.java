package org.son.haruharu.domain.replyrecommendation.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
import org.son.haruharu.domain.replyrecommendation.implement.ReplyRecommendationRoomGate;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 방 단위 스위치(#560): 전체 스위치 AND (방 칸 rooms.reply_recommendation_enabled OR 설정 room-ids 에 포함).
 * room-ids 는 기존 시험 방 호환용이고 비어 있어도 모든 방이 아니다.
 * 꺼진 방에서는 분석 요청·추천·사건·답장 연결이 모두 생기지 않아야 한다.
 */
class ReplyRecommendationRoomGateTest {

    private static final long TEST_ROOM = 75L;
    private static final long OTHER_ROOM = 12L;

    private static ReplyRecommendationProperties properties(boolean enabled, List<Long> roomIds) {
        return new ReplyRecommendationProperties(enabled, false, 10, 1000, Duration.ofMinutes(30), Duration.ofHours(24),
                new ReplyRecommendationProperties.Collection(true, List.of()),
                new ReplyRecommendationProperties.Snapshot(3000, 20, 5, Duration.ofDays(30)), roomIds);
    }

    private static Room room(long id, boolean switchOn) {
        return new Room(id, RoomStatus.ACTIVE, LocalDate.now(), 3, null, null).withReplyRecommendationEnabled(switchOn);
    }

    /** OTHER_ROOM 은 방 칸이 꺼진 방, TEST_ROOM 은 room-ids 에만 있는 방이다. */
    private static ReplyRecommendationRoomGate gate(boolean enabled, List<Long> roomIds, boolean otherRoomSwitch) {
        RoomReader rooms = mock(RoomReader.class);
        when(rooms.findById(OTHER_ROOM)).thenReturn(Optional.of(room(OTHER_ROOM, otherRoomSwitch)));
        when(rooms.findById(TEST_ROOM)).thenReturn(Optional.of(room(TEST_ROOM, false)));
        return new ReplyRecommendationRoomGate(properties(enabled, roomIds), rooms);
    }

    private static ReplyRecommendationRoomGate closedForOtherRoom() {
        return gate(true, List.of(TEST_ROOM), false);
    }

    @Test
    @DisplayName("전체 스위치가 켜져 있고 방 칸이 켜졌거나 room-ids 에 있으면 켜진다. room-ids 가 비어도 모든 방이 아니다")
    void givenSwitchCombinations_whenAsked_thenGlobalAndRoomOrList() {
        // 방 칸 × room-ids 포함 여부, 전체 스위치 켜짐
        assertThat(gate(true, List.of(), true).isOpen(OTHER_ROOM)).isTrue();
        assertThat(gate(true, List.of(), false).isOpen(OTHER_ROOM)).isFalse();
        assertThat(gate(true, List.of(TEST_ROOM), false).isOpen(TEST_ROOM)).isTrue();
        assertThat(gate(true, List.of(TEST_ROOM), false).isOpen(OTHER_ROOM)).isFalse();
        assertThat(gate(true, List.of(TEST_ROOM), true).isOpen(OTHER_ROOM)).isTrue();
        assertThat(gate(true, List.of(OTHER_ROOM), true).isOpen(OTHER_ROOM)).isTrue();
        // 전체 스위치가 꺼지면 방 칸·room-ids 와 무관하게 꺼짐
        assertThat(gate(false, List.of(), true).isOpen(OTHER_ROOM)).isFalse();
        assertThat(gate(false, List.of(TEST_ROOM), false).isOpen(TEST_ROOM)).isFalse();
        assertThat(gate(false, List.of(OTHER_ROOM), true).isOpen(OTHER_ROOM)).isFalse();
    }

    @Test
    @DisplayName("전체 스위치가 꺼졌거나 room-ids 로 판단이 끝나면 방을 조회하지 않고, 없는 방은 꺼짐이다")
    void givenGlobalOffOrListed_whenAsked_thenNoRoomLookup() {
        RoomReader rooms = mock(RoomReader.class);
        assertThat(new ReplyRecommendationRoomGate(properties(false, List.of()), rooms).isOpen(OTHER_ROOM)).isFalse();
        assertThat(new ReplyRecommendationRoomGate(properties(true, List.of(TEST_ROOM)), rooms).isOpen(TEST_ROOM))
                .isTrue();
        verifyNoInteractions(rooms);

        when(rooms.findById(OTHER_ROOM)).thenReturn(Optional.empty());
        assertThat(new ReplyRecommendationRoomGate(properties(true, List.of()), rooms).isOpen(OTHER_ROOM)).isFalse();
    }

    @Test
    @DisplayName("방 칸이 꺼진 방에 영상을 올리면 분석 요청을 넣지 않는다")
    void givenOtherRoom_whenVideoUploaded_thenNoAnalysisRequest() {
        AnalysisRequestRegistrar registrar = mock(AnalysisRequestRegistrar.class);
        ReplyRecommendationPublishListener listener = new ReplyRecommendationPublishListener(
                closedForOtherRoom(), registrar, Clock.systemUTC());

        listener.onVideoUploaded(new VideoUploadedEvent(1L, OTHER_ROOM, 2L, LocalDateTime.now()));
        verifyNoInteractions(registrar);

        listener.onVideoUploaded(new VideoUploadedEvent(3L, TEST_ROOM, 4L, LocalDateTime.now()));
        verify(registrar).registerPublished(eq(3L), eq(4L), any(Instant.class));
    }

    @Test
    @DisplayName("방 칸이 켜진 방에 영상을 올리면 room-ids 에 없어도 분석 요청을 넣는다")
    void givenRoomSwitchOn_whenVideoUploaded_thenAnalysisRequest() {
        AnalysisRequestRegistrar registrar = mock(AnalysisRequestRegistrar.class);
        ReplyRecommendationPublishListener listener = new ReplyRecommendationPublishListener(
                gate(true, List.of(), true), registrar, Clock.systemUTC());

        listener.onVideoUploaded(new VideoUploadedEvent(5L, OTHER_ROOM, 6L, LocalDateTime.now()));
        verify(registrar).registerPublished(eq(5L), eq(6L), any(Instant.class));
    }

    @Test
    @DisplayName("방 칸이 꺼진 방의 추천 요청·조회는 권한 확인 전에 FEATURE_DISABLED 로 숨긴다")
    void givenOtherRoom_whenEnsureOrGet_thenFeatureDisabled() {
        RecommendationAccessJudge judge = mock(RecommendationAccessJudge.class);
        ReplyRecommendationService service = new ReplyRecommendationService(properties(true, List.of(TEST_ROOM)),
                closedForOtherRoom(), judge,
                mock(ActiveVersionReader.class), mock(AnalysisRequestRegistrar.class),
                mock(RecommendationRequestStore.class), mock(RecommendationGuard.class),
                mock(RecommendationResolver.class), mock(EnsureRateLimiter.class), Clock.systemUTC());

        assertThat(service.ensure(OTHER_ROOM, 1L, 2L).reasonCode()).isEqualTo("FEATURE_DISABLED");
        assertThat(service.get(OTHER_ROOM, 1L, 2L).reasonCode()).isEqualTo("FEATURE_DISABLED");
        verifyNoInteractions(judge);
    }

    @Test
    @DisplayName("방 칸이 꺼진 방의 노출 사건과 영상 답장은 남기지 않는다")
    void givenOtherRoom_whenEventOrReply_thenNothingRecorded() {
        RecommendationAccessJudge judge = mock(RecommendationAccessJudge.class);
        RecommendationEventRecorder events = mock(RecommendationEventRecorder.class);
        RecommendationCollectionPolicy collection = mock(RecommendationCollectionPolicy.class);
        ReplyRecommendationEventService eventService = new ReplyRecommendationEventService(
                closedForOtherRoom(), judge, mock(RecommendationRequestStore.class), events, collection,
                Clock.systemUTC());
        eventService.record(OTHER_ROOM, 1L, 2L, null);
        verifyNoInteractions(judge, events, collection);

        ReplyLinkRecorder links = mock(ReplyLinkRecorder.class);
        ReplyRecommendationReplyListener replies = new ReplyRecommendationReplyListener(
                closedForOtherRoom(), links, collection, mock(PlatformTransactionManager.class),
                Clock.systemUTC());
        replies.onReplySaved(new VideoReplySavedEvent(9L, 8L, OTHER_ROOM, 1L, 2L, LocalDateTime.now(), null, null, null));
        verifyNoInteractions(links, collection);
    }
}
