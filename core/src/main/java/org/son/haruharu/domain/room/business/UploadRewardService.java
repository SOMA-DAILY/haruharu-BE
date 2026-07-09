package org.son.haruharu.domain.room.business;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.implement.DailyUploadReward;
import org.son.haruharu.domain.room.implement.DailyUploadRewardReader;
import org.son.haruharu.domain.room.implement.DailyUploadRewardWriter;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 일일 업로드 완주 보상 지급. 그 Day의 슬롯을 모두 채우면(완주) 구름을 자동 지급한다.
 * (roomMemberId, dayNumber)당 1회 — daily_upload_reward 테이블 UNIQUE로 멱등 보장. 재촬영 삭제/재업로드에도 재지급·회수 없음.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UploadRewardService {

    private final VideoPostReader videoPostReader;
    private final RoomMemberReader roomMemberReader;
    private final DailyUploadRewardReader dailyUploadRewardReader;
    private final DailyUploadRewardWriter dailyUploadRewardWriter;
    private final CoinService coinService;
    private final UploadRewardProperties uploadRewardProperties;
    private final Clock clock;

    // AFTER_COMMIT 리스너(UploadCompletedRewardListener)에서 호출되므로 REQUIRES_NEW로 새 트랜잭션을 연다(#158).
    // REQUIRED면 이미 커밋된 업로드 트랜잭션에 join되어 보상 지급 INSERT가 유실된다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void grantIfDayCompleted(Long roomMemberId, int dayNumber) {
        int slotCount = UploadSlot.ofDay(dayNumber).size();
        if (slotCount == 0) {
            return; // 미시작(day 0) 등 슬롯이 없는 Day
        }
        if (videoPostReader.countUploaded(roomMemberId, dayNumber) < slotCount) {
            return; // 아직 완주 아님
        }
        if (dailyUploadRewardReader.isRewarded(roomMemberId, dayNumber)) {
            return; // 이미 지급됨(멱등)
        }
        long amount = uploadRewardProperties.amountFor(dayNumber);
        if (amount <= 0) {
            return;
        }
        // 멱등 기록을 먼저 남긴다(UNIQUE 제약이 동시 이중지급을 차단). 같은 트랜잭션이라 credit 실패 시 함께 롤백된다.
        dailyUploadRewardWriter.save(DailyUploadReward.of(roomMemberId, dayNumber, amount, LocalDateTime.now(clock)));
        Long userId = roomMemberReader.getById(roomMemberId).userId();
        coinService.credit(CoinCreditCommand.uploadCompletion(userId, amount, roomMemberId, dayNumber));
    }
}
