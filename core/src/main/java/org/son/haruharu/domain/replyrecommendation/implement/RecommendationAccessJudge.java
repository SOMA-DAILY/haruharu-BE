package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.VideoReply;
import org.son.haruharu.domain.chat.implement.VideoReplyTarget;
import org.son.haruharu.domain.chat.implement.VideoReplyTargetReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.springframework.stereotype.Component;

/**
 * 추천 요청·조회·사건마다 권한을 다시 확인한다. 기존 영상 답장과 같은 정책이다:
 * 단체 채팅 멤버가 아니면 거절(403), 다른 방 영상이면 404(VideoReplyTargetReader 가 던진다),
 * 답장할 수 없는 영상이면 카드 없이 UNAVAILABLE.
 */
@Component
@RequiredArgsConstructor
public class RecommendationAccessJudge {

    private final ChatRoomReader chatRooms;
    private final ChatRoomMemberReader chatMembers;
    private final VideoReplyTargetReader targets;
    private final RoomReader rooms;
    private final RoomMemberReader roomMembers;
    private final BlockReader blocks;
    private final Clock clock;

    public RecommendationAccess judge(Long roomId, Long videoId, Long viewerId) {
        Optional<ChatRoom> chat = memberChat(roomId, viewerId);
        if (chat.isEmpty()) {
            return RecommendationAccess.denied(RecommendationAccess.Denial.NOT_MEMBER);
        }
        VideoReplyTarget target = targets.get(roomId, chat.get().id(), videoId, viewerId);
        if (target.isOwnedBy(viewerId)) {
            return RecommendationAccess.unavailable(RecommendationReason.OWN_VIDEO);
        }
        RecommendationReason reason = switch (target.reason()) {
            case AVAILABLE -> null;
            case DELETED, HIDDEN -> RecommendationReason.VIDEO_UNAVAILABLE;
            case BLOCKED -> RecommendationReason.BLOCKED;
            case OWNER_WITHDRAWN -> RecommendationReason.OWNER_WITHDRAWN;
            case OWNER_LEFT -> RecommendationReason.OWNER_LEFT;
        };
        if (reason != null) {
            return RecommendationAccess.unavailable(reason);
        }
        Optional<Room> room = rooms.findById(roomId);
        if (room.isEmpty()) {
            return RecommendationAccess.denied(RecommendationAccess.Denial.ROOM_NOT_FOUND);
        }
        if (!VideoReply.isRoomOpen(room.get().statusAt(LocalDateTime.now(clock)))) {
            return RecommendationAccess.unavailable(RecommendationReason.ROOM_CLOSED);
        }
        return new RecommendationAccess(null, null, target.post(), target.owner(), chat.get().id(),
                scopeOf(roomId, viewerId));
    }

    /** 사건 수집용: 멤버십과 영상의 방만 확인한다(이미 본 노출은 답장 가능 여부와 무관하게 받는다). */
    public boolean isMemberOfVideoRoom(Long roomId, Long videoId, Long viewerId) {
        Optional<ChatRoom> chat = memberChat(roomId, viewerId);
        chat.ifPresent(room -> targets.get(roomId, room.id(), videoId, viewerId));
        return chat.isPresent();
    }

    /** 시청자와 차단 관계(방향 무관)인 이 방 멤버를 뺀 범위. */
    public AccessScope scopeOf(Long roomId, Long viewerId) {
        Set<Long> related = blocks.getRelatedUserIds(viewerId);
        if (related.isEmpty()) {
            return AccessScope.base();
        }
        Set<Long> roomUsers = roomMembers.getByRoomId(roomId).stream()
                .map(RoomMember::userId)
                .collect(Collectors.toSet());
        return AccessScope.of(related.stream().filter(roomUsers::contains).toList());
    }

    private Optional<ChatRoom> memberChat(Long roomId, Long viewerId) {
        ChatRoom chat = chatRooms.getByRoomId(roomId);
        return chatMembers.findActiveMembership(chat.id(), viewerId).map(ignored -> chat);
    }
}
