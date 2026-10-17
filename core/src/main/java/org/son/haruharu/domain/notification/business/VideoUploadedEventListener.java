package org.son.haruharu.domain.notification.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.notification.implement.PushBundle;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.son.haruharu.infrastructure.fcm.PushStyle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class VideoUploadedEventListener {

    private final RoomMemberReader roomMemberReader;
    private final PushSendService pushSendService;
    private final BlockReader blockReader;
    private final PushBundleService pushBundleService;

    public VideoUploadedEventListener(RoomMemberReader roomMemberReader, PushSendService pushSendService,
                                      BlockReader blockReader, PushBundleService pushBundleService) {
        this.roomMemberReader = roomMemberReader;
        this.pushSendService = pushSendService;
        this.blockReader = blockReader;
        this.pushBundleService = pushBundleService;
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
        // 방마다 한 칸으로 묶는다(#505): 첫 업로드는 울리고, 60분 안의 업로드는 올린 사람 이름을 모두 붙여 조용히 교체한다.
        // 알림함에는 남기지 않는 타입이라(inboxVisible=false) 푸시만 보낸다. 묶음 판단이 실패하면 지금처럼 울린다.
        Map<String, String> data = Map.of("roomId", event.roomId().toString());
        String key = PushBundle.uploadKey(event.roomId());
        String name = uploader.displayPseudonym();
        List<PushBundleService.UploadDecision> decisions = decide(event.roomId(), name, recipients);
        Map<PushGroup, List<Long>> groups = decisions.stream().collect(Collectors.groupingBy(
                d -> new PushGroup(d.alert(), d.names()),
                Collectors.mapping(PushBundleService.UploadDecision::userId, Collectors.toList())));
        groups.forEach((group, userIds) -> pushSendService.push(userIds, NotificationType.VIDEO_UPLOADED, data,
                PushBundle.uploadTitle(group.names()), NotificationType.VIDEO_UPLOADED.body(),
                group.alert() ? PushStyle.alert(key) : PushStyle.quiet(key)));
    }

    // 같은 방 첫 업로드가 동시에 들어오면 첫 행 삽입이 겹쳐 실패할 수 있어 한 번 다시 판단한다.
    private List<PushBundleService.UploadDecision> decide(Long roomId, String name, List<Long> recipients) {
        for (int attempt = 1; ; attempt++) {
            try {
                return pushBundleService.decideUpload(roomId, name, recipients);
            } catch (Exception e) {
                if (attempt < 2) {
                    log.warn("업로드 푸시 묶음 판단 재시도: roomId={} reason={}", roomId, e.getClass().getSimpleName());
                    continue;
                }
                log.error("업로드 푸시 묶음 판단 실패, 보통 알림으로 발송: roomId={}", roomId, e);
                return recipients.stream()
                        .map(id -> new PushBundleService.UploadDecision(id, true, List.of(name)))
                        .toList();
            }
        }
    }

    private record PushGroup(boolean alert, List<String> names) {
    }
}
