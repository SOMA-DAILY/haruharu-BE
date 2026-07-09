package org.son.haruharu.domain.room.business;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 영상 업로드 커밋 후 완주 보상 지급을 시도한다.
 * AFTER_COMMIT이라 업로드가 확정된 뒤 실행되어 완주 판정(countUploaded)이 방금 업로드를 포함하며,
 * 보상 지급 실패가 업로드 트랜잭션을 롤백하지 않는다. 멱등은 daily_upload_reward UNIQUE가 보장한다.
 */
@Component
@RequiredArgsConstructor
public class UploadCompletedRewardListener {

    private final UploadRewardService uploadRewardService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(VideoUploadedEvent event) {
        uploadRewardService.grantIfDayCompleted(event.roomMemberId(), event.dayNumber());
    }
}
