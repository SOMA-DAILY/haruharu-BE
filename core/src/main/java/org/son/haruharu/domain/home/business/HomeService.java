package org.son.haruharu.domain.home.business;

import java.time.Clock;
import java.time.LocalDateTime;

import org.son.haruharu.domain.home.business.dto.HomeResponse;
import org.son.haruharu.domain.room.business.MatchResultService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.dto.PendingMatchResultResponse;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메인 홈 화면 집계(초대코드 모델). 참여 중인 방(RoomService)과 발표 후 매칭 결과(MatchResultService)만 조율한다.
 * 자동매칭 큐·코인 잔액 의존은 새 MVP 전환으로 제거했다. 도메인 간 조율은 각 도메인의 Service를 통해서만 한다.
 */
@Service
@Transactional(readOnly = true)
public class HomeService {

    private final RoomService roomService;
    private final MatchResultService matchResultService;
    private final Clock clock;

    public HomeService(RoomService roomService, MatchResultService matchResultService, Clock clock) {
        this.roomService = roomService;
        this.matchResultService = matchResultService;
        this.clock = clock;
    }

    public HomeResponse getHome(Long userId) {
        RoomResponse room = roomService.findMyRoom(userId).orElse(null);
        PendingMatchResultResponse pendingResult = matchResultService.findPendingResult(userId).orElse(null);
        return HomeResponse.of(LocalDateTime.now(clock), room, pendingResult);
    }
}
