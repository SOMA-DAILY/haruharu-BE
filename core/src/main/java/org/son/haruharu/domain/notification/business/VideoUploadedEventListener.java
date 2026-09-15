package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class VideoUploadedEventListener {

    private final RoomMemberReader roomMemberReader;
    private final PushSendService pushSendService;
    private final BlockReader blockReader;

    public VideoUploadedEventListener(RoomMemberReader roomMemberReader,
                                      PushSendService pushSendService, BlockReader blockReader) {
        this.roomMemberReader = roomMemberReader;
        this.pushSendService = pushSendService;
        this.blockReader = blockReader;
    }

    // 커밋 후 발송 — 업로드 저장 트랜잭션이 푸시 실패로 롤백되지 않도록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(VideoUploadedEvent event) {
        List<RoomMember> members = roomMemberReader.getByRoomId(event.roomId());
        RoomMember uploader = members.stream()
                .filter(m -> m.id().equals(event.roomMemberId()))
                .findFirst()
                .orElse(null);
        if (uploader == null) {
            return;
        }
        // 업로더를 차단한 수신자는 제외한다(#408, 단방향 — 배치 조회로 수신자별 단건 검사 N+1을 피한다).
        Set<Long> blockers = blockReader.getBlockerUserIds(uploader.userId());
        List<Long> recipients = members.stream()
                .filter(m -> !m.id().equals(event.roomMemberId()))
                .map(RoomMember::userId)
                .filter(id -> !blockers.contains(id))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        pushSendService.sendToUsers(recipients, NotificationType.VIDEO_UPLOADED,
                Map.of("roomId", event.roomId().toString()), uploader.displayPseudonym());
    }
}
