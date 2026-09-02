package org.son.haruharu.domain.mission.business;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MissionActivityEventListener {

    private static final Logger log = LoggerFactory.getLogger(MissionActivityEventListener.class);

    private final MissionActivityCollectorService collectorService;

    public MissionActivityEventListener(MissionActivityCollectorService collectorService) {
        this.collectorService = collectorService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVideo(VideoUploadedEvent event) {
        try {
            collectorService.recordVideo(event.roomId(), event.roomMemberId(), event.occurredAt());
        } catch (RuntimeException exception) {
            log.atError()
                    .setCause(exception)
                    .addKeyValue("event", "MISSION_VIDEO_ACTIVITY_COLLECTION_FAILED")
                    .addKeyValue("videoPostId", event.videoPostId())
                    .addKeyValue("roomId", event.roomId())
                    .addKeyValue("roomMemberId", event.roomMemberId())
                    .log("저장 완료 영상의 미션 활동 수집 실패");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChat(ChatMessageSentEvent event) {
        if (event.chatRoomType() != ChatRoomType.GROUP || event.roomId() == null) {
            return;
        }
        try {
            collectorService.recordGroupChat(event.roomId(), event.senderUserId(), event.occurredAt());
        } catch (RuntimeException exception) {
            log.atError()
                    .setCause(exception)
                    .addKeyValue("event", "MISSION_CHAT_ACTIVITY_COLLECTION_FAILED")
                    .addKeyValue("chatMessageId", event.chatMessageId())
                    .addKeyValue("roomId", event.roomId())
                    .addKeyValue("senderUserId", event.senderUserId())
                    .log("저장 완료 그룹채팅의 미션 활동 수집 실패");
        }
    }
}
