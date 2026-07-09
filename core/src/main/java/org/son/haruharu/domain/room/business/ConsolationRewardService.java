package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.event.MatchConsolationEvent;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 미매칭 위로 구름 지급 유스케이스(#143). 결과 발표(Day3 22:00) 이후의 ACTIVE 방에서
 * MatchResult가 없는 멤버 전원에게 지급한다(완주 여부 무관, 완주 보상과 중복 허용).
 * "정확 시각"이 아닌 상태 기반 판정이므로 22:00 실행을 놓쳐도 방 종료(익일 09:00) 전 재실행으로 회수된다(#170 원칙).
 * 멱등은 코인 원장(MATCH_CONSOLATION + refId=roomId)이 보장한다.
 */
@Service
@Transactional(readOnly = true)
public class ConsolationRewardService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final MatchResultReader matchResultReader;
    private final CoinService coinService;
    private final ConsolationRewardProperties consolationRewardProperties;
    private final ApplicationEventPublisher eventPublisher;

    public ConsolationRewardService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                    MatchResultReader matchResultReader, CoinService coinService,
                                    ConsolationRewardProperties consolationRewardProperties,
                                    ApplicationEventPublisher eventPublisher) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.matchResultReader = matchResultReader;
        this.coinService = coinService;
        this.consolationRewardProperties = consolationRewardProperties;
        this.eventPublisher = eventPublisher;
    }

    /** 결과 발표가 지난 ACTIVE 방의 미매칭 멤버에게 위로 구름을 지급한다. 반환값은 새로 지급된 유저 수. */
    @Transactional
    public int grantForAnnouncedRooms(LocalDateTime now) {
        int granted = 0;
        for (Room room : roomReader.getActive()) {
            if (!isResultAnnounced(room, now)) {
                continue;
            }
            List<Long> newlyGranted = roomMemberReader.getByRoomId(room.id()).stream()
                    .filter(member -> !matchResultReader.hasMatch(member.id()))
                    .map(RoomMember::userId)
                    .filter(userId -> coinService.grantMatchConsolation(
                            userId, room.id(), consolationRewardProperties.amount()))
                    .toList();
            if (!newlyGranted.isEmpty()) {
                eventPublisher.publishEvent(new MatchConsolationEvent(
                        room.id(), newlyGranted, consolationRewardProperties.amount()));
                granted += newlyGranted.size();
            }
        }
        return granted;
    }

    /** 마지막 Day의 결과 발표 시각(CONFIRM_HOUR)이 지났는지. 최종선택 마감과 같은 앵커(dateOfDay)에서 파생한다. */
    private boolean isResultAnnounced(Room room, LocalDateTime now) {
        if (room.currentDayAt(now, UploadSlot.dayStartHour()) != room.totalDays()) {
            return false;
        }
        LocalDateTime resultAt = room.dateOfDay(room.totalDays(), UploadSlot.dayStartHour())
                .atStartOfDay().plusHours(MatchingQueue.CONFIRM_HOUR);
        return !now.isBefore(resultAt);
    }
}
