package org.son.haruharu.domain.home.business;

import java.time.Clock;
import java.time.LocalDateTime;

import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.home.business.dto.HomeResponse;
import org.son.haruharu.domain.matching.business.MatchingService;
import org.son.haruharu.domain.matching.business.dto.LastApplyResultResponse;
import org.son.haruharu.domain.matching.business.dto.QueueStatusResponse;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.MatchResultService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.dto.PendingMatchResultResponse;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메인 홈 화면 집계. 도메인 간 조율은 각 도메인의 Service를 통해서만 한다(storage 직접 참조 금지). */
@Service
@Transactional(readOnly = true)
public class HomeService {

    private final RoomService roomService;
    private final MatchingService matchingService;
    private final CoinService coinService;
    private final MatchResultService matchResultService;
    private final Clock clock;

    public HomeService(RoomService roomService, MatchingService matchingService,
                       CoinService coinService, MatchResultService matchResultService, Clock clock) {
        this.roomService = roomService;
        this.matchingService = matchingService;
        this.coinService = coinService;
        this.matchResultService = matchResultService;
        this.clock = clock;
    }

    public HomeResponse getHome(Long userId) {
        long pointBalance = coinService.getWallet(userId).totalBalance();
        QueueStatusResponse queue = matchingService.getQueueStatus(userId);
        RoomResponse room = roomService.findMyRoom(userId).orElse(null);
        PendingMatchResultResponse pendingResult = matchResultService.findPendingResult(userId).orElse(null);
        LastApplyResultResponse lastApplyResult = matchingService.getLastApplyResult(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        return HomeResponse.of(now, pointBalance, queue, room, pendingResult, lastApplyResult,
                nextAtHour(now, MatchingQueue.APPLY_DEADLINE_HOUR),
                nextAtHour(now, MatchingQueue.CONFIRM_HOUR), UploadSlot.dayStartHour());
    }

    /** now 이후 가장 가까운 해당 시각(오늘 그 시각 전이면 오늘, 아니면 내일). 홈 카운트다운 기준. */
    private LocalDateTime nextAtHour(LocalDateTime now, int hour) {
        LocalDateTime todayAt = now.toLocalDate().atTime(hour, 0);
        return now.isBefore(todayAt) ? todayAt : todayAt.plusDays(1);
    }
}
