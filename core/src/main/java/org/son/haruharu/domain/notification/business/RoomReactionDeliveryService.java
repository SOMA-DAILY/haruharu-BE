package org.son.haruharu.domain.notification.business;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.video.implement.VideoPostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 지연 반응 이벤트와 퇴장 정리가 같은 원본 방 잠금을 공유한다. 푸시는 이 트랜잭션이 끝난 뒤 보낸다. */
@Service
@RequiredArgsConstructor
public class RoomReactionDeliveryService {
    private final VideoPostRepository videos;
    private final RoomMemberReader members;
    private final RoomReader rooms;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean canDeliver(Long videoId, Long authorId, Long reactorId) {
        var video = videos.findById(videoId).orElse(null);
        if (video == null || video.deletedAt() != null || video.isHidden()) return false;
        var source = members.findById(video.roomMemberId()).orElse(null);
        if (source == null || rooms.findByIdForUpdate(source.roomId()).isEmpty()) return false;
        // 잠금을 기다리는 동안 퇴장했을 수 있으므로 활성 멤버 목록을 다시 조회한다.
        var active = members.getByRoomId(source.roomId());
        boolean authorActive = active.stream().anyMatch(m -> m.id().equals(video.roomMemberId()) && m.userId().equals(authorId));
        boolean reactorActive = active.stream().anyMatch(m -> m.userId().equals(reactorId));
        return authorActive && reactorActive;
    }
}
