package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.implement.VideoCaptionHistory;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Component;

/**
 * 영상의 게시 시각 T 이전·T 이전에 이용 가능했던 현재 방 기록을 모은다(명세 5장).
 * 단체 채팅(사용자 글·운영 글·영상 답장)과 이전 영상(T 시점 캡션 + 분석된 장면 설명)만 넣는다.
 * 1:1 대화·다른 방 기록·시청 범위 밖(차단) 사람의 기록은 넣지 않는다. 기록을 자르지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RoomHistoryCollector {

    private final ChatMessageReader chatMessages;
    private final RoomMemberReader roomMembers;
    private final VideoPostReader videoPosts;
    private final VideoCaptionHistory captionHistory;
    private final AnalysisPipelineRepository pipeline;
    private final Clock clock;

    public HistorySnapshot collect(Long roomId, Long chatRoomId, VideoPost current, AccessScope scope) {
        ZoneId zone = clock.getZone();
        LocalDateTime cutoff = current.createdAt();
        List<HistoryRecord> records = new ArrayList<>();
        List<ExcludedSource> excluded = new ArrayList<>();

        collectVideos(roomId, current, cutoff, scope, zone, records, excluded);
        collectChat(chatRoomId, cutoff, scope, zone, records, excluded);

        Set<String> included = new HashSet<>();
        records.forEach(record -> included.add(record.sourceId()));
        List<HistoryRecord> ordered = records.stream()
                .map(record -> record.withParents(record.parentSourceIds().stream().filter(included::contains).toList()))
                .sorted(HistoryRecord.CHRONOLOGICAL)
                .toList();
        return HistorySnapshot.of(ordered, excluded);
    }

    private void collectVideos(Long roomId, VideoPost current, LocalDateTime cutoff, AccessScope scope, ZoneId zone,
                               List<HistoryRecord> records, List<ExcludedSource> excluded) {
        Map<Long, Long> userByMember = new HashMap<>();
        for (RoomMember member : roomMembers.getByRoomId(roomId)) {
            userByMember.put(member.id(), member.userId());
        }
        List<VideoPost> previous = new ArrayList<>();
        for (Long memberId : userByMember.keySet()) {
            for (VideoPost post : videoPosts.findActiveByRoomMember(memberId)) {
                if (!post.id().equals(current.id()) && post.createdAt().isBefore(cutoff)) {
                    previous.add(post);
                }
            }
        }
        List<Long> ids = previous.stream().map(VideoPost::id).toList();
        Map<Long, String> captionsAtCutoff = captionHistory.captionsAt(ids, cutoff);
        Map<Long, String> descriptions = pipeline.findLatestVisualDescriptions(ids);
        for (VideoPost post : previous) {
            Long authorId = userByMember.get(post.roomMemberId());
            String sourceId = "video:" + post.id();
            if (scope.excludes(authorId)) {
                excluded.add(new ExcludedSource(sourceId, ExcludedSource.ACCESS_SCOPE));
                continue;
            }
            String caption = captionsAtCutoff.getOrDefault(post.id(), post.caption() == null ? "" : post.caption());
            String text = videoText(caption, descriptions.get(post.id()));
            String at = AiReplyJson.iso(post.createdAt(), zone);
            records.add(new HistoryRecord(sourceId, String.valueOf(authorId), HistoryRecord.KIND_VIDEO,
                    HistoryRecord.ROLE_ROOM, at, at, AiReplyJson.sha256(text), text, List.of()));
        }
    }

    private void collectChat(Long chatRoomId, LocalDateTime cutoff, AccessScope scope, ZoneId zone,
                             List<HistoryRecord> records, List<ExcludedSource> excluded) {
        for (ChatMessage message : chatMessages.findHistoryBefore(chatRoomId, cutoff)) {
            String sourceId = "chat:" + message.id();
            if (message.isAdminDeleted()) {
                excluded.add(new ExcludedSource(sourceId, ExcludedSource.ADMIN_DELETED));
                continue;
            }
            if (!message.isSystem() && scope.excludes(message.senderId())) {
                excluded.add(new ExcludedSource(sourceId, ExcludedSource.ACCESS_SCOPE));
                continue;
            }
            String text = message.content() == null ? "" : message.content().strip();
            if (text.isEmpty()) {
                excluded.add(new ExcludedSource(sourceId, ExcludedSource.NO_TEXT));
                continue;
            }
            List<String> parents = new ArrayList<>();
            if (message.videoPostId() != null) {
                parents.add("video:" + message.videoPostId());
            }
            if (message.replyToMessageId() != null) {
                parents.add("chat:" + message.replyToMessageId());
            }
            String actor = message.isSystem() ? HistoryRecord.SYSTEM_ACTOR : String.valueOf(message.senderId());
            String at = AiReplyJson.iso(message.createdAt(), zone);
            records.add(new HistoryRecord(sourceId, actor, HistoryRecord.KIND_CHAT, HistoryRecord.ROLE_ROOM, at, at,
                    AiReplyJson.sha256(text), text, parents));
        }
    }

    static String videoText(String caption, String description) {
        String line = "캡션: " + (caption.isBlank() ? "(없음)" : caption.strip());
        return description == null || description.isBlank() ? line : line + "\n장면: " + description.strip();
    }
}
