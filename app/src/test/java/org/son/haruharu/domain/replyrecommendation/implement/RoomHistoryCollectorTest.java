package org.son.haruharu.domain.replyrecommendation.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatMessageType;
import org.son.haruharu.domain.chat.implement.ChatSenderType;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.implement.VideoCaptionHistory;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;

@ExtendWith(MockitoExtension.class)
class RoomHistoryCollectorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 8, 14, 0);

    @Mock ChatMessageReader chatMessages;
    @Mock RoomMemberReader roomMembers;
    @Mock VideoPostReader videoPosts;
    @Mock VideoCaptionHistory captionHistory;
    @Mock AnalysisPipelineRepository pipeline;

    private RoomHistoryCollector collector() {
        return new RoomHistoryCollector(chatMessages, roomMembers, videoPosts, captionHistory, pipeline,
                Clock.system(SEOUL));
    }

    private static VideoPost post(long id, long memberId, String caption, LocalDateTime createdAt) {
        return new VideoPost(id, memberId, createdAt.withMinute(0), null, caption, "https://m/" + id + ".mp4", null,
                false, null, null, createdAt);
    }

    private static ChatMessage text(long id, Long sender, String content, LocalDateTime at, Long videoId, Long replyTo) {
        return new ChatMessage(id, 3L, sender, content, at, videoId, null, replyTo, null, null, ChatMessageType.TEXT,
                null, null, null, ChatSenderType.USER, null, null, null, false);
    }

    @Test
    @DisplayName("게시 시각 이전 기록만, 시청 범위 밖·운영자 삭제·글 없는 메시지는 빼고 넣는다")
    void givenRoomRecords_whenCollect_thenOnlyAuthorizedRecordsBeforeCutoff() {
        var current = post(77, 1, "오늘 과제 중", T);
        given(roomMembers.getByRoomId(5L)).willReturn(List.of(
                new RoomMember(1L, 5L, 20L, "FEMALE", "서연", null, T.minusDays(1)),
                new RoomMember(2L, 5L, 10L, "MALE", "도윤", null, T.minusDays(1)),
                new RoomMember(3L, 5L, 30L, "MALE", "지훈", null, T.minusDays(1))));
        given(videoPosts.findActiveByRoomMember(1L)).willReturn(List.of(post(70, 1, "어제 영상", T.minusHours(20)), current));
        given(videoPosts.findActiveByRoomMember(2L)).willReturn(List.of());
        given(videoPosts.findActiveByRoomMember(3L)).willReturn(List.of(post(71, 3, "지훈 영상", T.minusHours(2))));
        given(captionHistory.captionsAt(any(), any())).willReturn(Map.of(70L, "어제 원래 캡션"));
        given(pipeline.findLatestVisualDescriptions(any())).willReturn(Map.of(70L, "책상 위 노트북"));
        var deleted = new ChatMessage(13L, 3L, 20L, "지운 글", T.minusHours(3), null, null, null, null, null,
                ChatMessageType.TEXT, null, T.minusHours(1), null, ChatSenderType.USER, null, null, null, false);
        given(chatMessages.findHistoryBefore(3L, T)).willReturn(List.of(
                text(10, 20L, "내일 발표 준비해야 돼", T.minusHours(19), null, null),
                text(11, 10L, "화이팅!", T.minusHours(18), 70L, null),
                text(12, 30L, "차단한 사람 글", T.minusHours(17), null, null),
                deleted,
                text(14, 10L, "  ", T.minusHours(4), null, null)));

        HistorySnapshot snapshot = collector().collect(5L, 3L, current, AccessScope.of(List.of(30L)));

        assertThat(snapshot.records()).extracting(HistoryRecord::sourceId)
                .containsExactly("video:70", "chat:10", "chat:11");
        HistoryRecord video = snapshot.records().get(0);
        assertThat(video.text()).isEqualTo("캡션: 어제 원래 캡션\n장면: 책상 위 노트북");
        assertThat(video.actorId()).isEqualTo("20");
        assertThat(snapshot.records().get(2).parentSourceIds()).containsExactly("video:70");
        assertThat(snapshot.excluded()).extracting(ExcludedSource::sourceId, ExcludedSource::reason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("video:71", ExcludedSource.ACCESS_SCOPE),
                        org.assertj.core.groups.Tuple.tuple("chat:12", ExcludedSource.ACCESS_SCOPE),
                        org.assertj.core.groups.Tuple.tuple("chat:13", ExcludedSource.ADMIN_DELETED),
                        org.assertj.core.groups.Tuple.tuple("chat:14", ExcludedSource.NO_TEXT));
        assertThat(snapshot.status()).isEqualTo(HistorySnapshot.COMPLETE);
        assertThat(video.occurredAt()).isEqualTo("2026-11-07T09:00:00Z");
    }

    @Test
    @DisplayName("기록이 없으면 NO_RECORDS 로 표시한다")
    void givenNoRecords_whenCollect_thenNoRecordsStatus() {
        var current = post(77, 1, null, T);
        given(roomMembers.getByRoomId(5L)).willReturn(List.of(new RoomMember(1L, 5L, 20L, "FEMALE", "서연", null, T)));
        given(videoPosts.findActiveByRoomMember(1L)).willReturn(List.of(current));
        given(captionHistory.captionsAt(any(), any())).willReturn(Map.of());
        given(pipeline.findLatestVisualDescriptions(any())).willReturn(Map.of());
        given(chatMessages.findHistoryBefore(3L, T)).willReturn(List.of());

        HistorySnapshot snapshot = collector().collect(5L, 3L, current, AccessScope.base());

        assertThat(snapshot.records()).isEmpty();
        assertThat(snapshot.status()).isEqualTo(HistorySnapshot.NO_RECORDS);
    }
}
