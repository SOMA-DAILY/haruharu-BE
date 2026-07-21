package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.room.business.dto.DemoRoomResponse;
import org.son.haruharu.domain.room.business.exception.DemoRoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.user.business.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 데모 방 라이프사이클(#265·#272). 시간에 의존하지 않도록 방을 실제 rooms 테이블에 demo=true·demo_day로 만들고,
 * 배치(getActive/getScheduledPending demo=false)에서 격리해 무만료·무진행을 보장한다.
 * 방 생성 시 호출자 본인 + 가상 참여자 5명(총 6명)을 멤버로 구성한다 — 가상 참여자 계정·프로필은
 * 소유 도메인 서비스(UserService·ProfileService)를 경유해 시드한다(Service-to-Service 조율). 피드 영상은 후속.
 */
@Service
@Transactional(readOnly = true)
public class DemoRoomService {

    private static final int TOTAL_DAYS = 3;

    /** 가상 참여자 스펙(고정). 나이 25~30, 성비 3F·2M(본인 MALE 기준 3:3). 사진은 외부 플레이스홀더 URL(presign 통과). */
    private record VirtualSpec(String nickname, String gender, int age, String location, String photoUrl) {}

    private static final List<VirtualSpec> VIRTUAL_SPECS = List.of(
            new VirtualSpec("서연", "FEMALE", 27, "서울", "https://picsum.photos/seed/haruharu-demo-1/600/800"),
            new VirtualSpec("지우", "FEMALE", 25, "경기", "https://picsum.photos/seed/haruharu-demo-2/600/800"),
            new VirtualSpec("하은", "FEMALE", 29, "서울", "https://picsum.photos/seed/haruharu-demo-3/600/800"),
            new VirtualSpec("도윤", "MALE", 28, "서울", "https://picsum.photos/seed/haruharu-demo-4/600/800"),
            new VirtualSpec("시우", "MALE", 26, "인천", "https://picsum.photos/seed/haruharu-demo-5/600/800")
    );

    private final RoomReader roomReader;
    private final RoomWriter roomWriter;
    private final RoomMemberReader roomMemberReader;
    private final RoomMemberWriter roomMemberWriter;
    private final IdentityService identityService;
    private final UserService userService;
    private final ProfileService profileService;
    private final Clock clock;

    public DemoRoomService(RoomReader roomReader, RoomWriter roomWriter,
                           RoomMemberReader roomMemberReader, RoomMemberWriter roomMemberWriter,
                           IdentityService identityService, UserService userService,
                           ProfileService profileService, Clock clock) {
        this.roomReader = roomReader;
        this.roomWriter = roomWriter;
        this.roomMemberReader = roomMemberReader;
        this.roomMemberWriter = roomMemberWriter;
        this.identityService = identityService;
        this.userService = userService;
        this.profileService = profileService;
        this.clock = clock;
    }

    /**
     * 데모 방 생성(Day1, ACTIVE) + 호출자 본인 + 가상 5명(총 6명) 멤버 구성. 이미 데모 방이 있으면 그대로 반환(멱등).
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
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startedAt = today.atTime(UploadSlot.dayStartHour(), 0);
        Room room = roomWriter.save(Room.createDemo(today, TOTAL_DAYS, 1, startedAt));

        // 1) 호출자 본인. gender는 room_members NOT NULL — 데모 계정은 보통 프로비저닝돼 본인인증값이 있지만,
        //    없더라도 방 생성이 깨지지 않도록 기본값(MALE)으로 폴백한다(성별은 데모 표시용).
        VerifiedIdentity identity = identityService.findVerified(userId).orElse(null);
        String gender = identity != null ? identity.gender() : "MALE";
        LocalDate birthday = identity != null ? identity.birthday() : null;
        roomMemberWriter.save(RoomMember.of(room.id(), userId, gender, birthday, now));

        // 2) 가상 참여자 5명(#272). 계정·프로필은 소유 도메인 서비스로 시드(Service-to-Service).
        VIRTUAL_SPECS.forEach(spec -> {
            Long virtualUserId = userService.createDemoVirtualAccount();
            profileService.seedDemoProfile(virtualUserId, spec.nickname(), spec.location(), spec.photoUrl());
            roomMemberWriter.save(RoomMember.of(
                    room.id(), virtualUserId, spec.gender(), today.minusYears(spec.age()), now));
        });

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
