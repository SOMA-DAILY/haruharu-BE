package org.son.haruharu.devsupport;

import org.son.haruharu.common.time.AdjustableClock;
import org.son.haruharu.devsupport.dto.DevClockResponse;
import org.son.haruharu.devsupport.dto.DevRoomResponse;
import org.son.haruharu.devsupport.exception.DevMemberAlreadyInRoomException;
import org.son.haruharu.devsupport.exception.DevMemberNotVerifiedException;
import org.son.haruharu.devsupport.exception.DevRoomNotActiveException;
import org.son.haruharu.devsupport.exception.InvalidDevDayException;
import org.son.haruharu.devsupport.exception.InvalidDevMembersException;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.room.business.ConsolationRewardService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

/**
 * [dev·local 전용, #180] 테스트 방 즉시 생성·시간대 지정 유스케이스.
 * 기존 방 도메인 객체와 유스케이스로 원하는 상태의 테스트 방을 재현한다.
 */
@Profile({"local", "dev"})
@Service
@Transactional(readOnly = true)
public class DevRoomService {

    private static final int TOTAL_DAYS = 3;
    private static final int MIN_MEMBERS = 2;
    private static final int MAX_MEMBERS = 6;

    private final IdentityService identityService;
    private final RoomService roomService;
    private final RoomReader roomReader;
    private final RoomWriter roomWriter;
    private final RoomMemberWriter roomMemberWriter;
    private final ConsolationRewardService consolationRewardService;
    private final RoomMissionSlotService roomMissionSlotService;
    private final DevRoomCleaner devRoomCleaner;
    private final AdjustableClock adjustableClock;

    public DevRoomService(IdentityService identityService, RoomService roomService,
                          RoomReader roomReader, RoomWriter roomWriter,
                          RoomMemberWriter roomMemberWriter, ConsolationRewardService consolationRewardService,
                          RoomMissionSlotService roomMissionSlotService,
                          DevRoomCleaner devRoomCleaner, AdjustableClock adjustableClock) {
        this.identityService = identityService;
        this.roomService = roomService;
        this.roomReader = roomReader;
        this.roomWriter = roomWriter;
        this.roomMemberWriter = roomMemberWriter;
        this.consolationRewardService = consolationRewardService;
        this.roomMissionSlotService = roomMissionSlotService;
        this.devRoomCleaner = devRoomCleaner;
        this.adjustableClock = adjustableClock;
    }

    /**
     * 지정 계정들로 방을 즉시 ACTIVE로 생성한다. started_at은 테스트 방의
     * 시작일 09:00 앵커로 역산한다 — 현재 시각이 요청한 day에 해당하도록 시작일을 계산.
     * time을 주면 가상 시계를 그 방의 (day, time) 절대 시각에 고정한다.
     */
    @Transactional
    public DevRoomResponse createRoom(List<Long> userIds, Integer day, LocalTime time) {
        validateMembers(userIds);
        int targetDay = day != null ? day : 1;
        validateDay(targetDay, TOTAL_DAYS);

        List<RoomMember> memberTemplates = userIds.stream()
                .map(this::toVerifiedMemberTemplate)
                .toList();

        // (지금-9h)의 날짜가 Day{targetDay}가 되도록 시작일 역산 — 하루 경계 09:00과 동일한 앵커
        LocalDate startDate = adjustableClock.realNow().minusHours(Room.DAY_START_HOUR)
                .toLocalDate().minusDays(targetDay - 1L);
        LocalDateTime startedAt = startDate.atTime(Room.DAY_START_HOUR, 0);
        Room room = roomWriter.save(
                new Room(null, RoomStatus.ACTIVE, startDate, TOTAL_DAYS, startedAt, null));
        roomMissionSlotService.createDefaultsForRoom(
                room.id(), TOTAL_DAYS, startedAt, startedAt.plusDays(TOTAL_DAYS));

        roomMemberWriter.saveAll(memberTemplates.stream()
                .map(t -> RoomMember.of(room.id(), t.userId(), t.gender(), t.birthday(),
                        adjustableClock.realNow()))
                .toList());

        if (time != null) {
            adjustableClock.fix(virtualDateTime(room, targetDay, time));
        }
        return new DevRoomResponse(room.id(), room.scheduledDate(), room.startedAt(),
                room.currentDayAt(LocalDateTime.now(adjustableClock)),
                clockStatus());
    }

    /** 가상 시계를 해당 방의 (day, time) 절대 시각에 고정한다. */
    public DevClockResponse setRoomTime(Long roomId, int day, LocalTime time) {
        Room room = getActiveRoom(roomId);
        validateDay(day, room.totalDays());
        adjustableClock.fix(virtualDateTime(room, day, time));
        return clockStatus();
    }

    public DevClockResponse clockStatus() {
        return new DevClockResponse(
                adjustableClock.isFixed(),
                LocalDateTime.now(adjustableClock),
                adjustableClock.realNow(),
                adjustableClock.expiresAt().orElse(null));
    }

    public DevClockResponse resetClock() {
        adjustableClock.reset();
        return clockStatus();
    }

    /** 방 수동 종료(ENDED 전이 상태 테스트용). */
    @Transactional
    public void terminateRoom(Long roomId) {
        Room room = getActiveRoom(roomId);
        roomWriter.save(room.ended(LocalDateTime.now(adjustableClock)));
    }

    /** 위로 구름 수동 트리거 — 발표 시각이 지난 모든 ACTIVE 방 대상(기존 유스케이스 그대로). */
    @Transactional
    public int grantConsolation() {
        return consolationRewardService.grantForAnnouncedRooms(LocalDateTime.now(adjustableClock));
    }

    /** 테스트 방과 파생 데이터(영상·선택·매치·채팅) 전체 삭제. */
    @Transactional
    public void deleteRoom(Long roomId) {
        roomReader.findById(roomId).orElseThrow(RoomNotFoundException::new);
        devRoomCleaner.deleteRoomCascade(roomId);
    }

    private void validateMembers(List<Long> userIds) {
        if (userIds == null || userIds.size() < MIN_MEMBERS || userIds.size() > MAX_MEMBERS
                || new HashSet<>(userIds).size() != userIds.size()) {
            throw new InvalidDevMembersException();
        }
    }

    private void validateDay(int day, int totalDays) {
        if (day < 1 || day > totalDays) {
            throw new InvalidDevDayException();
        }
    }

    private RoomMember toVerifiedMemberTemplate(Long userId) {
        VerifiedIdentity identity = identityService.findVerified(userId)
                .orElseThrow(() -> new DevMemberNotVerifiedException(userId));
        if (roomService.hasCurrentRoom(userId)) {
            throw new DevMemberAlreadyInRoomException(userId);
        }
        return RoomMember.of(null, userId, identity.gender(), identity.birthday(), null);
    }

    private Room getActiveRoom(Long roomId) {
        Room room = roomReader.findById(roomId).orElseThrow(RoomNotFoundException::new);
        if (room.status() != RoomStatus.ACTIVE || room.startedAt() == null) {
            throw new DevRoomNotActiveException();
        }
        return room;
    }

    /**
     * (day, time)을 그 방의 절대 시각으로 변환한다. 하루 경계가 09:00이므로
     * 09시 이전(새벽)은 해당 Day의 익일 달력 날짜에 속한다.
     */
    private LocalDateTime virtualDateTime(Room room, int day, LocalTime time) {
        LocalDate base = room.dateOfDay(day);
        return time.getHour() >= Room.DAY_START_HOUR
                ? base.atTime(time)
                : base.plusDays(1).atTime(time);
    }
}
