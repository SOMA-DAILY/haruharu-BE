package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.room.business.dto.DemoRoomResponse;
import org.son.haruharu.domain.room.business.exception.DemoRoomNotFoundException;
import org.son.haruharu.domain.room.business.exception.NotDemoAccountException;
import org.son.haruharu.domain.room.implement.DemoRoomFinder;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.user.business.UserService;
import org.son.haruharu.domain.video.business.VideoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    /** 데모 계정 기본 구름 잔액. 부팅 시더·리셋이 공유해 지급/재지급 금액이 어긋나지 않게 한다(#267). */
    public static final long DEMO_COINS = 200L;

    /** 가상 참여자 스펙(고정). 나이 25~30, 성비 3F·2M(본인 MALE 기준 3:3). 사진은 외부 플레이스홀더 URL(presign 통과). */
    private record VirtualSpec(String nickname, String gender, int age, String location, String photoUrl) {}

    private static final List<VirtualSpec> VIRTUAL_SPECS = List.of(
            new VirtualSpec("서연", "FEMALE", 27, "서울", "https://picsum.photos/seed/haruharu-demo-1/600/800"),
            new VirtualSpec("지우", "FEMALE", 25, "경기", "https://picsum.photos/seed/haruharu-demo-2/600/800"),
            new VirtualSpec("하은", "FEMALE", 29, "서울", "https://picsum.photos/seed/haruharu-demo-3/600/800"),
            new VirtualSpec("도윤", "MALE", 28, "서울", "https://picsum.photos/seed/haruharu-demo-4/600/800"),
            new VirtualSpec("시우", "MALE", 26, "인천", "https://picsum.photos/seed/haruharu-demo-5/600/800")
    );

    private final RoomWriter roomWriter;
    private final RoomMemberReader roomMemberReader;
    private final RoomMemberWriter roomMemberWriter;
    private final IdentityService identityService;
    private final UserService userService;
    private final ProfileService profileService;
    private final VideoService videoService;
    private final CoinService coinService;
    private final DemoRoomFinder demoRoomFinder;
    private final DemoMatchService demoMatchService;
    private final Clock clock;

    public DemoRoomService(RoomWriter roomWriter,
                           RoomMemberReader roomMemberReader, RoomMemberWriter roomMemberWriter,
                           IdentityService identityService, UserService userService,
                           ProfileService profileService, VideoService videoService,
                           CoinService coinService, DemoRoomFinder demoRoomFinder,
                           DemoMatchService demoMatchService, Clock clock) {
        this.roomWriter = roomWriter;
        this.roomMemberReader = roomMemberReader;
        this.roomMemberWriter = roomMemberWriter;
        this.identityService = identityService;
        this.userService = userService;
        this.profileService = profileService;
        this.videoService = videoService;
        this.coinService = coinService;
        this.demoRoomFinder = demoRoomFinder;
        this.demoMatchService = demoMatchService;
        this.clock = clock;
    }

    /**
     * 데모 방 생성(Day1, ACTIVE) + 호출자 본인 + 가상 5명(총 6명) 멤버 구성. 이미 데모 방이 있으면 그대로 반환(멱등).
     * demo_day 파생이라 startedAt은 안전값(오늘 dayStartHour)으로 두되 Day는 시각에 의존하지 않는다.
     */
    @Transactional
    public DemoRoomResponse createOrGet(Long userId) {
        Optional<Room> existing = demoRoomFinder.find(userId);
        if (existing.isPresent()) {
            Room room = existing.get();
            return new DemoRoomResponse(room.id(), currentDay(room));
        }

        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime startedAt = today.atTime(UploadSlot.dayStartHour(), 0);
        Room room = roomWriter.save(Room.createDemo(today, TOTAL_DAYS, 1, startedAt));

        List<Long> memberIds = new ArrayList<>();
        // 1) 호출자 본인. gender는 room_members NOT NULL — 데모 계정은 보통 프로비저닝돼 본인인증값이 있지만,
        //    없더라도 방 생성이 깨지지 않도록 기본값(MALE)으로 폴백한다(성별은 데모 표시용).
        VerifiedIdentity identity = identityService.findVerified(userId).orElse(null);
        String gender = identity != null ? identity.gender() : "MALE";
        LocalDate birthday = identity != null ? identity.birthday() : null;
        memberIds.add(roomMemberWriter.save(RoomMember.of(room.id(), userId, gender, birthday, now)).id());

        // 2) 가상 참여자 5명(#272). 계정·프로필은 소유 도메인 서비스로 시드(Service-to-Service).
        for (VirtualSpec spec : VIRTUAL_SPECS) {
            Long virtualUserId = userService.createDemoVirtualAccount();
            profileService.seedDemoProfile(virtualUserId, spec.nickname(), spec.location(), spec.photoUrl());
            memberIds.add(roomMemberWriter.save(RoomMember.of(
                    room.id(), virtualUserId, spec.gender(), today.minusYears(spec.age()), now)).id());
        }

        // 3) Day1 피드 시드(#274). 열람 게이트가 "본인 업로드 슬롯만"이라 본인 포함 전원 시드해야 리뷰어가 피드를 본다.
        videoService.seedDemoFeed(memberIds, 1);

        return new DemoRoomResponse(room.id(), 1);
    }

    /** 데모 방 Day 진행(1→2→3, totalDays로 캡). 새로 진행한 Day의 피드를 시드한다. 데모 방이 없으면 404. */
    @Transactional
    public DemoRoomResponse advanceDay(Long userId) {
        Room room = demoRoomFinder.find(userId).orElseThrow(DemoRoomNotFoundException::new);
        int current = room.demoDay() == null ? 1 : room.demoDay();
        int next = Math.min(TOTAL_DAYS, current + 1);
        Room saved = roomWriter.save(room.withDemoDay(next));
        // 새 Day로 진행했을 때만 그 Day 피드를 1회 시드(이미 최종일이면 중복 시드 방지).
        if (next > current) {
            List<Long> memberIds = roomMemberReader.getByRoomId(saved.id()).stream()
                    .map(RoomMember::id).toList();
            videoService.seedDemoFeed(memberIds, next);
        }
        return new DemoRoomResponse(saved.id(), next);
    }

    /**
     * 데모 데이터 제자리 리셋(#267). 리뷰어가 참가신청부터 다시 시연할 수 있도록 초기 상태로 되돌린다.
     * 방·멤버·가상계정은 재사용하고(하드삭제 안 함), Day를 1로 되돌린 뒤 피드를 재시드하고 구름을 리시드한다.
     * 실서비스 데이터·배치는 demo=true 방/데모 계정에만 작용하므로 무영향.
     */
    @Transactional
    public DemoRoomResponse reset(Long userId) {
        // 방어: /api/v1/demo/** 게이트가 이미 데모 계정만 통과시키지만, 구름 원장 하드삭제가 파괴적이라 이중 확인.
        if (!userService.isDemoAccount(userId)) {
            throw new NotDemoAccountException();
        }

        // 구름 리시드는 방 유무와 무관하게 항상 수행 — 방 없이도 참가신청 재시연이 가능하도록.
        coinService.resetDemoWallet(userId, DEMO_COINS);

        Optional<Room> existing = demoRoomFinder.find(userId);
        if (existing.isEmpty()) {
            return new DemoRoomResponse(null, 1);
        }

        Room reset = roomWriter.save(existing.get().withDemoDay(1));
        List<Long> memberIds = roomMemberReader.getByRoomId(reset.id()).stream()
                .map(RoomMember::id).toList();
        videoService.resetDemoFeed(memberIds, 1);
        // 최종선택 결과·채팅까지 지워야 참가신청부터 재시연된다(#266). 하드삭제 이유는 DemoMatchService 참고.
        demoMatchService.purge(memberIds);
        return new DemoRoomResponse(reset.id(), 1);
    }

    private int currentDay(Room room) {
        return room.currentDayAt(LocalDateTime.now(clock), UploadSlot.dayStartHour());
    }
}
