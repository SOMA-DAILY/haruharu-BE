package org.son.haruharu.demo.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.son.haruharu.domain.room.business.event.MutualMatchEvent;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.MatchResultWriter;
import org.son.haruharu.domain.room.implement.PseudonymPool;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.user.implement.UserReader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 방 재생성·리셋(#352 PR2). 로그인 시점에 "지금(now) 기준" 신선한 방을 세워, 시드 없이도 항상 활동 중인 방을 보여준다.
 * <p>
 * 동시 로그인(리뷰어 다중 접속)에 대비해 <b>데모 유저 행 비관적 락(#321)</b>으로 재생성을 직렬화한다 — 락을 쥔 첫 요청만
 * 방을 만들고, 뒤따르는 요청은 이미 생긴 현재 방을 재사용한다(중복 방 방지). 방 생성은 초대코드 없이 core 로직을 직접 조합하고,
 * {@code RoomStartedEvent} 한 번으로 GROUP 채팅·미션 자동 생성 파이프라인을 그대로 탄다(도메인 무변경).
 */
@Service
@Transactional(readOnly = true)
public class DemoRoomService {

    private static final String DEMO_GENDER = "MALE"; // 데모 계정 성별(3:3에서 한 자리). 유령이 나머지를 채운다.
    private static final int TOTAL_DAYS = 3;
    private static final int STARTS_HOURS_AGO = 2; // 과거 슬롯이 몇 개 있도록 2시간 전 시작

    private final UserReader userReader;
    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final RoomWriter roomWriter;
    private final RoomMemberWriter roomMemberWriter;
    private final DemoGhostProvisioner demoGhostProvisioner;
    private final DemoRoomCleaner demoRoomCleaner;
    private final DemoVideoSeeder demoVideoSeeder;
    private final MatchResultReader matchResultReader;
    private final MatchResultWriter matchResultWriter;
    private final RoomMissionSlotService roomMissionSlotService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public DemoRoomService(UserReader userReader, RoomReader roomReader, RoomMemberReader roomMemberReader,
                           RoomWriter roomWriter, RoomMemberWriter roomMemberWriter,
                           DemoGhostProvisioner demoGhostProvisioner, DemoRoomCleaner demoRoomCleaner,
                           DemoVideoSeeder demoVideoSeeder,
                           MatchResultReader matchResultReader, MatchResultWriter matchResultWriter,
                           RoomMissionSlotService roomMissionSlotService,
                           ApplicationEventPublisher eventPublisher, Clock clock) {
        this.userReader = userReader;
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.roomWriter = roomWriter;
        this.roomMemberWriter = roomMemberWriter;
        this.demoGhostProvisioner = demoGhostProvisioner;
        this.demoRoomCleaner = demoRoomCleaner;
        this.demoVideoSeeder = demoVideoSeeder;
        this.matchResultReader = matchResultReader;
        this.matchResultWriter = matchResultWriter;
        this.roomMissionSlotService = roomMissionSlotService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 현재 데모 방이 있으면 재사용하고, 없으면(최초·종료) 옛 방을 정리한 뒤 새 방을 세운다. 로그인이 호출한다. */
    @Transactional
    public void rebuildIfStale(Long demoUserId) {
        userReader.lockById(demoUserId); // 동시 재생성 직렬화(#321)
        LocalDateTime now = LocalDateTime.now(clock);

        List<Long> demoRoomIds = demoRoomIds(demoUserId);
        if (!demoRoomIds.isEmpty() && roomReader.findCurrent(demoRoomIds, now).isPresent()) {
            return; // 현재 방 있음 → 재사용 (콘텐츠 신선도 재생성은 PR3)
        }
        demoRoomIds.forEach(demoRoomCleaner::deleteRoomCascade); // 종료·잔존 데모 방 정리(누적 방지)
        build(demoUserId, now);
    }

    /** 데모 방과 그 안의 모든 데이터를 물리 삭제한다(수동 리셋). */
    @Transactional
    public void reset(Long demoUserId) {
        userReader.lockById(demoUserId);
        demoRoomIds(demoUserId).forEach(demoRoomCleaner::deleteRoomCascade);
    }

    /**
     * 데모 계정과 반대 성별 유령 하나를 즉시 상호 성사시킨다(#352 PR4). 최종선택 타이밍을 기다리지 않고
     * "성사 → 1:1(DIRECT) 채팅"을 시연한다. {@code MutualMatchEvent} 를 발행해 기존 DIRECT 채팅·매칭 푸시 파이프라인을
     * 그대로 탄다(FinalSelectionResolver 와 동일). 이미 성사됐으면(멱등) 아무 것도 하지 않는다.
     */
    @Transactional
    public void forceMatch(Long demoUserId) {
        userReader.lockById(demoUserId);
        LocalDateTime now = LocalDateTime.now(clock);

        List<Long> roomIds = demoRoomIds(demoUserId);
        Optional<Room> current = roomIds.isEmpty() ? Optional.empty() : roomReader.findCurrent(roomIds, now);
        if (current.isEmpty()) {
            return; // 방이 없으면 성사할 대상이 없다(로그인이 먼저 방을 세운다)
        }
        Long roomId = current.get().id();
        List<RoomMember> members = roomMemberReader.getByRoomId(roomId);
        RoomMember me = members.stream().filter(m -> demoUserId.equals(m.userId())).findFirst().orElse(null);
        if (me == null) {
            return;
        }
        RoomMember partner = members.stream()
                .filter(m -> !demoUserId.equals(m.userId()))
                .filter(m -> !me.gender().equals(m.gender())) // 반대 성별(최종선택 대상)
                .findFirst().orElse(null);
        if (partner == null || matchResultReader.isMatched(me.id(), partner.id())) {
            return; // 상대 없음 또는 이미 성사(멱등)
        }
        MatchResult saved = matchResultWriter.save(MatchResult.of(me.id(), partner.id(), now));
        // 커밋 후 ChatRoomAutoCreateListener 가 DIRECT 1:1 채팅을 연다.
        eventPublisher.publishEvent(new MutualMatchEvent(roomId, saved.id(), demoUserId, partner.userId()));
    }

    private List<Long> demoRoomIds(Long demoUserId) {
        return roomMemberReader.findByUserId(demoUserId).stream()
                .map(RoomMember::roomId)
                .distinct()
                .toList();
    }

    private void build(Long demoUserId, LocalDateTime now) {
        LocalDateTime startsAt = now.minusHours(STARTS_HOURS_AGO).truncatedTo(ChronoUnit.HOURS);
        LocalDateTime opensAt = startsAt.plusDays(TOTAL_DAYS); // 최종선택은 먼 미래 → now 는 ACTIVE 구간
        Room room = roomWriter.save(new Room(null, RoomStatus.ACTIVE, startsAt.toLocalDate(), TOTAL_DAYS,
                startsAt, null, startsAt, opensAt, opensAt.plusHours(1), opensAt.plusHours(2), opensAt.plusHours(3)));
        roomMissionSlotService.createDefaultsForRoom(room.id(), TOTAL_DAYS, startsAt, opensAt);

        List<RoomMember> members = new ArrayList<>();
        members.add(RoomMember.of(room.id(), demoUserId, DEMO_GENDER, null, now)
                .withPseudonym(PseudonymPool.assign(DEMO_GENDER, 0)));
        for (DemoGhost ghost : demoGhostProvisioner.ensureGhosts()) {
            int joinIndex = (int) members.stream().filter(member -> ghost.gender().equals(member.gender())).count();
            members.add(RoomMember.of(room.id(), ghost.userId(), ghost.gender(), null, now)
                    .withPseudonym(PseudonymPool.assign(ghost.gender(), joinIndex)));
        }
        List<RoomMember> saved = roomMemberWriter.saveAll(members);

        // 과거 슬롯에 전원의 영상을 심어 피드를 채운다(현재 슬롯은 리뷰어 촬영용으로 비움).
        demoVideoSeeder.seed(saved, startsAt, now);

        // 멤버 커밋 후 GroupChatAutoCreateListener가 채팅을 만들고, 미션은 별도 JIT 배치가 슬롯별로 배정한다.
        eventPublisher.publishEvent(new RoomStartedEvent(room.id()));
    }
}
