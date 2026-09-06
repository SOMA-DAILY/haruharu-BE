package org.son.haruharu.admin.domain.room.business;

import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.admin.domain.room.business.command.AssignGenderCommand;
import org.son.haruharu.admin.domain.room.business.command.CreateAdminRoomCommand;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomCreatedResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomInviteResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomMemberResponse;
import org.son.haruharu.admin.domain.room.implement.AdminRoomReader;
import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.implement.PseudonymPool;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어드민 방 운영(생성·코드 발급·성별 부여) 유스케이스(#318).
 * <p>
 * 방 생성과 코드 발급은 core 의 서로 다른 서비스가 담당하지만 운영 관점에선 한 동작이라
 * 여기서 <b>한 트랜잭션으로 묶는다</b> — 코드 없는 방이 남지 않게. 어드민 전용 조합이므로
 * core 를 어드민 관심사로 오염시키지 않고 admin 모듈이 조합한다(architecture 규칙).
 */
@Service
@Transactional(readOnly = true)
public class AdminRoomCommandService {

    private final RoomService roomService;
    private final RoomInviteService roomInviteService;
    private final RoomMissionSlotService roomMissionSlotService;
    private final RoomMemberReader roomMemberReader;
    private final RoomMemberWriter roomMemberWriter;
    private final AdminRoomReader adminRoomReader;

    public AdminRoomCommandService(RoomService roomService, RoomInviteService roomInviteService,
                                   RoomMissionSlotService roomMissionSlotService,
                                   RoomMemberReader roomMemberReader, RoomMemberWriter roomMemberWriter,
                                   AdminRoomReader adminRoomReader) {
        this.roomService = roomService;
        this.roomInviteService = roomInviteService;
        this.roomMissionSlotService = roomMissionSlotService;
        this.roomMemberReader = roomMemberReader;
        this.roomMemberWriter = roomMemberWriter;
        this.adminRoomReader = adminRoomReader;
    }

    /** 방 + 미션 슬롯 + 코드 생성. 검증·저장 실패 시 전체를 롤백한다(같은 트랜잭션). */
    @Transactional
    public AdminRoomCreatedResponse createRoomWithInvite(CreateAdminRoomCommand command, Long adminId) {
        Long roomId = roomService.createRoom(command.toRoomCommand());
        roomMissionSlotService.createForRoom(roomId, command.totalDays(), command.startsAt(),
                command.finalSelectionOpensAt(), command.missionSlots());
        RoomInvite invite = roomInviteService.issueFor(
                roomId, command.maxUses(), command.inviteExpiresAt(), createdBy(adminId));
        return new AdminRoomCreatedResponse(roomId, invite.code());
    }

    /** 코드 재발급. 기존 ACTIVE 코드는 EXPIRED 로 닫힌다(core reissueFor). 없는 방이면 404. */
    @Transactional
    public AdminRoomInviteResponse reissueInvite(Long roomId, Integer maxUses, LocalDateTime expiresAt, Long adminId) {
        adminRoomReader.requireExists(roomId); // scalar 존재 확인: room lock 재조회가 fresh state를 보게 한다
        return AdminRoomInviteResponse.from(
                roomInviteService.reissueFor(roomId, maxUses, expiresAt, createdBy(adminId)));
    }

    /**
     * 방 취소(#320). 모집이 안 차서 접거나 잘못 만든 방을 치울 때. CANCELLED 는 sticky 라
     * 이후 reconciler 전이·초대코드 입장에서 모두 제외된다. 이미 종료된 방은 core 가 거부한다.
     */
    @Transactional
    public void cancelRoom(Long roomId) {
        adminRoomReader.requireExists(roomId); // 없는 방은 ADMIN_ROOM_NOT_FOUND(404), Entity preload는 하지 않는다
        roomService.cancel(roomId);
    }

    /** 방의 코드 이력(최신순). 없는 방이면 404. */
    public List<AdminRoomInviteResponse> getInvites(Long roomId) {
        adminRoomReader.getById(roomId); // 존재 확인 게이트
        return roomInviteService.getByRoomId(roomId).stream()
                .map(AdminRoomInviteResponse::from)
                .toList();
    }

    /**
     * 멤버 성별 부여. 초대코드 입장 시 프로필 성별이 없었으면(gender=null) 가명(#393)도 배정되지 못한 채 남으므로,
     * 운영진이 성별을 채울 때 <b>가명도 함께 백필</b>한다 — 이게 없으면 그룹채팅 등에서 표시 이름이 계속 비게 된다.
     * 이미 가명이 있으면(입장 시 배정됐거나 재부여) 유지한다 — 대화 중 표시 이름이 갑자기 바뀌지 않게.
     * memberId 가 해당 방 소속인지 확인해 다른 방 멤버를 경로만 바꿔 수정하는 것을 막는다.
     * 허용값(MALE·FEMALE) 검증은 도메인(RoomMember.withGender)이 소유한다 — 여기서 중복 검사하지 않는다.
     */
    @Transactional
    public AdminRoomMemberResponse assignGender(Long roomId, Long memberId, AssignGenderCommand command) {
        RoomMember member = roomMemberReader.getById(memberId);
        if (!member.roomId().equals(roomId)) {
            throw new RoomMemberNotFoundException();
        }
        RoomMember gendered = member.withGender(command.gender());
        RoomMember toSave = gendered.pseudonym() != null ? gendered
                : gendered.withPseudonym(PseudonymPool.assign(command.gender(),
                        (int) roomMemberReader.countByRoomIdAndGender(roomId, command.gender())));
        return AdminRoomMemberResponse.from(roomMemberWriter.save(toSave));
    }

    /** 코드 발급 주체 표기. RoomInvite.createdBy 는 "admin id 또는 SYSTEM" 규약(#312). */
    private String createdBy(Long adminId) {
        return "admin:" + adminId;
    }
}
