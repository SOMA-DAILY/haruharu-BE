package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.dto.DemoRoomResponse;
import org.son.haruharu.domain.room.business.exception.DemoRoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 데모 방 라이프사이클(#265). 시간에 의존하지 않도록 방을 실제 rooms 테이블에 demo=true·demo_day로 만들고,
 * 배치(getActive/getScheduledPending demo=false)에서 격리해 무만료·무진행을 보장한다.
 * 1차 범위는 방 골격(호출자 본인만 멤버) — 가상 참여자·피드 영상 시드는 후속.
 */
@Service
@Transactional(readOnly = true)
public class DemoRoomService {

    private static final int TOTAL_DAYS = 3;

    private final RoomReader roomReader;
    private final RoomWriter roomWriter;
    private final RoomMemberReader roomMemberReader;
    private final RoomMemberWriter roomMemberWriter;
    private final IdentityService identityService;
    private final Clock clock;

    public DemoRoomService(RoomReader roomReader, RoomWriter roomWriter,
                           RoomMemberReader roomMemberReader, RoomMemberWriter roomMemberWriter,
                           IdentityService identityService, Clock clock) {
        this.roomReader = roomReader;
        this.roomWriter = roomWriter;
        this.roomMemberReader = roomMemberReader;
        this.roomMemberWriter = roomMemberWriter;
        this.identityService = identityService;
        this.clock = clock;
    }

    /**
     * 데모 방 생성(Day1, ACTIVE) + 호출자를 멤버로 추가. 이미 데모 방이 있으면 그대로 반환(멱등).
     * demo_day 파생이라 startedAt은 안전값(오늘 dayStartHour)으로 두되 Day는 시각에 의존하지 않는다.
     */
    @Transactional
    public DemoRoomResponse createOrGet(Long userId) {
        Optional<Room> existing = findDemoRoom(userId);
        if (existing.isPresent()) {
            Room room = existing.get();
            return new DemoRoomResponse(room.id(), currentDay(room));
        }

        LocalDate today = LocalDate.now(clock);
        LocalDateTime startedAt = today.atTime(UploadSlot.dayStartHour(), 0);
        Room room = roomWriter.save(Room.createDemo(today, TOTAL_DAYS, 1, startedAt));

        // gender는 room_members NOT NULL. 데모 계정은 보통 프로비저닝(#269)돼 본인인증값이 있지만,
        // 없더라도 방 생성이 깨지지 않도록 기본값(MALE)으로 폴백한다(성별은 데모 표시용).
        VerifiedIdentity identity = identityService.findVerified(userId).orElse(null);
        String gender = identity != null ? identity.gender() : "MALE";
        LocalDate birthday = identity != null ? identity.birthday() : null;
        roomMemberWriter.save(RoomMember.of(room.id(), userId, gender, birthday, LocalDateTime.now(clock)));

        return new DemoRoomResponse(room.id(), 1);
    }

    /** 데모 방 Day 진행(1→2→3, totalDays로 캡). 데모 방이 없으면 404. */
    @Transactional
    public DemoRoomResponse advanceDay(Long userId) {
        Room room = findDemoRoom(userId).orElseThrow(DemoRoomNotFoundException::new);
        int next = Math.min(TOTAL_DAYS, (room.demoDay() == null ? 1 : room.demoDay()) + 1);
        Room saved = roomWriter.save(room.withDemoDay(next));
        return new DemoRoomResponse(saved.id(), next);
    }

    /** 호출자가 속한 방 중 데모(비종료) 방을 찾는다. 유저 본인 방 조회라 배치 격리(demo=false)와 무관. */
    private Optional<Room> findDemoRoom(Long userId) {
        return roomMemberReader.findByUserId(userId).stream()
                .map(member -> roomReader.findById(member.roomId()))
                .flatMap(Optional::stream)
                .filter(room -> room.demo() && room.status() != RoomStatus.ENDED)
                .findFirst();
    }

    private int currentDay(Room room) {
        return room.currentDayAt(LocalDateTime.now(clock), UploadSlot.dayStartHour());
    }
}
