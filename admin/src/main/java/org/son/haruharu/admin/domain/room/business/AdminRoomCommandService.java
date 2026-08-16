package org.son.haruharu.admin.domain.room.business;

import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.admin.domain.room.business.command.AssignGenderCommand;
import org.son.haruharu.admin.domain.room.business.command.CreateAdminRoomCommand;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomCreatedResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomInviteResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomMemberResponse;
import org.son.haruharu.admin.domain.room.implement.AdminRoomReader;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.RoomService;
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
    private final RoomMemberReader roomMemberReader;
    private final RoomMemberWriter roomMemberWriter;
    private final AdminRoomReader adminRoomReader;

    public AdminRoomCommandService(RoomService roomService, RoomInviteService roomInviteService,
                                   RoomMemberReader roomMemberReader, RoomMemberWriter roomMemberWriter,
                                   AdminRoomReader adminRoomReader) {
        this.roomService = roomService;
        this.roomInviteService = roomInviteService;
        this.roomMemberReader = roomMemberReader;
        this.roomMemberWriter = roomMemberWriter;
        this.adminRoomReader = adminRoomReader;
    }

    /** 방 생성 + 코드 발급. 스케줄 시각 검증은 core 가 하며, 실패 시 코드도 발급되지 않는다(같은 트랜잭션). */
    @Transactional
    public AdminRoomCreatedResponse createRoomWithInvite(CreateAdminRoomCommand command, Long adminId) {
        Long roomId = roomService.createRoom(command.toRoomCommand());
        RoomInvite invite = roomInviteService.issueFor(
                roomId, command.maxUses(), command.inviteExpiresAt(), createdBy(adminId));
        return new AdminRoomCreatedResponse(roomId, invite.code());
    }

    /** 코드 재발급. 기존 ACTIVE 코드는 EXPIRED 로 닫힌다(core reissueFor). 없는 방이면 404. */
    @Transactional
    public AdminRoomInviteResponse reissueInvite(Long roomId, Integer maxUses, LocalDateTime expiresAt, Long adminId) {
        adminRoomReader.getById(roomId); // 존재 확인 게이트
        return AdminRoomInviteResponse.from(
                roomInviteService.reissueFor(roomId, maxUses, expiresAt, createdBy(adminId)));
    }

    /** 방의 코드 이력(최신순). 없는 방이면 404. */
    public List<AdminRoomInviteResponse> getInvites(Long roomId) {
        adminRoomReader.getById(roomId); // 존재 확인 게이트
        return roomInviteService.getByRoomId(roomId).stream()
                .map(AdminRoomInviteResponse::from)
                .toList();
    }

    /**
     * 멤버 성별 부여. 초대코드 입장은 gender=null 이라(온보딩 미수집) 운영진이 신청폼 기준으로 채운다.
     * memberId 가 해당 방 소속인지 확인해 다른 방 멤버를 경로만 바꿔 수정하는 것을 막는다.
     * 허용값(MALE·FEMALE) 검증은 도메인(RoomMember.withGender)이 소유한다 — 여기서 중복 검사하지 않는다.
     */
    @Transactional
    public AdminRoomMemberResponse assignGender(Long roomId, Long memberId, AssignGenderCommand command) {
        RoomMember member = roomMemberReader.getById(memberId);
        if (!member.roomId().equals(roomId)) {
            throw new RoomMemberNotFoundException();
        }
        return AdminRoomMemberResponse.from(roomMemberWriter.save(member.withGender(command.gender())));
    }

    /** 코드 발급 주체 표기. RoomInvite.createdBy 는 "admin id 또는 SYSTEM" 규약(#312). */
    private String createdBy(Long adminId) {
        return "admin:" + adminId;
    }
}
