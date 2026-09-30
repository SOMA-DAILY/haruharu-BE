package org.son.haruharu.domain.mission.business;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.mission.implement.RoomMissionSettingReader;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 저장이 커밋된 영상·그룹채팅을 미션 활동으로 수집한다. 미션이 꺼진 방(#449)은 수집기를 부르지 않아
 * 수집기의 새 트랜잭션(REQUIRES_NEW)과 쓰기 쿼리를 열지 않는다. 설정 조회도 try 안에 두어
 * 실패가 이미 저장된 영상·채팅 요청으로 전파되지 않게 한다.
 */
@Component
public class MissionActivityEventListener {

    private static final Logger log = LoggerFactory.getLogger(MissionActivityEventListener.class);

    private final MissionActivityCollectorService collectorService;
    private final RoomMissionSettingReader roomMissionSettingReader;

    public MissionActivityEventListener(MissionActivityCollectorService collectorService,
                                        RoomMissionSettingReader roomMissionSettingReader) {
        this.collectorService = collectorService;
        this.roomMissionSettingReader = roomMissionSettingReader;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVideo(VideoUploadedEvent event) {
        try {
            if (!roomMissionSettingReader.isMissionEnabled(event.roomId())) {
                return;
            }
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
            if (!roomMissionSettingReader.isMissionEnabled(event.roomId())) {
                return;
            }
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
