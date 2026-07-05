package org.son.haruharu.domain.home.business;

import java.time.Clock;
import java.time.LocalDateTime;

import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.home.business.dto.HomeResponse;
import org.son.haruharu.domain.matching.business.MatchingService;
import org.son.haruharu.domain.matching.business.dto.QueueStatusResponse;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.RoomService;
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
    private final Clock clock;

    public HomeService(RoomService roomService, MatchingService matchingService,
                       CoinService coinService, Clock clock) {
        this.roomService = roomService;
        this.matchingService = matchingService;
        this.coinService = coinService;
        this.clock = clock;
    }

    public HomeResponse getHome(Long userId) {
        long pointBalance = coinService.getWallet(userId).totalBalance();
        QueueStatusResponse queue = matchingService.getQueueStatus(userId);
        RoomResponse room = roomService.findMyRoom(userId).orElse(null);
        return HomeResponse.of(pointBalance, queue, room,
                nextConfirmAt(LocalDateTime.now(clock)), UploadSlot.dayStartHour());
    }

    private LocalDateTime nextConfirmAt(LocalDateTime now) {
        LocalDateTime todayConfirmAt = now.toLocalDate().atTime(MatchingQueue.CONFIRM_HOUR, 0);
        return now.isBefore(todayConfirmAt) ? todayConfirmAt : todayConfirmAt.plusDays(1);
    }
}
