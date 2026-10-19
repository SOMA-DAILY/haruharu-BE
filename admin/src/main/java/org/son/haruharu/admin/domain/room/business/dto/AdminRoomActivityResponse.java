package org.son.haruharu.admin.domain.room.business.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminRoomActivityResponse(
        String type, long id, LocalDateTime occurredAt, Integer day, Participant actor, Participant target,
        String content, Video video, String reactionType, String answer, LocalDateTime answeredAt,
        String replyType) {
    public record Participant(long userId, String name) {}
    public record Video(long id, String videoUrl, String thumbnailUrl, String caption, boolean hidden, Long missionId, LocalDateTime slotStart) {}
    public record Counts(long videos, long chats, long reactions, long questions) {}
    public record MemberCounts(long userId, String name, long videos, long chats, long reactionsSent,
                               long reactionsReceived, long questionsReceived) {}
    public record Summary(Counts counts, List<MemberCounts> members) {}
}
