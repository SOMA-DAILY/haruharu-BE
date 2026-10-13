package org.son.haruharu.admin.domain.room.implement;

import java.time.LocalDateTime;

/** 관리자 전용 활동 조회 프로젝션. 질문 발신자 정보는 유저 API에서 사용하지 않는다. */
public record RoomActivity(
        String type, long id, LocalDateTime occurredAt, Long actorUserId, Long targetUserId,
        String content, Long videoId, String videoUrl, String thumbnailUrl, String caption,
        String reactionType, String answer, LocalDateTime answeredAt, boolean hidden, Long missionId, LocalDateTime slotStart,
        // 채팅의 답장 종류: MESSAGE(글 답장) · VIDEO(영상 답장) · null(답장 아님, 채팅 외 활동)
        String replyType) {
    public record Count(String type, Long actorUserId, Long targetUserId, long count) {}
    public record Filter(Long roomId, LocalDateTime from, LocalDateTime to, Long userId, String type) {}
    public record Participant(long userId, String name) {}
    public record Statistics(long videos, long chats, long reactions, long questions,
                             long reactionsReceived, long questionsReceived) {}
    public record MemberStatistics(Participant participant, Statistics statistics) {}
}
