package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;

import org.son.haruharu.domain.room.business.dto.JoinRoomResponse;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotFoundException;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotUsableException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.InviteCodeGenerator;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomEntryPath;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomInviteReader;
import org.son.haruharu.domain.room.implement.RoomInviteWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대코드로 방 입장(#312). 앱 서버 전용 유스케이스다 — 어드민은 코드 발급(RoomInviteService)만 쓰고
 * 본인확인(identity) 빈도 두지 않는다.
 * <p>
 * {@code haruharu.room.join-identity-required}(기본 false)가 켜지면 PASS 본인확인을 하지 않은 계정의
 * 새 입장을 403 IDENTITY_REQUIRED 로 막는다(#514). 계정당 한 번 인증하면 이후 입장은 그대로 된다.
 */
@Service
@Transactional(readOnly = true)
public class RoomJoinService {

    private final RoomInviteReader roomInviteReader;
    private final RoomInviteWriter roomInviteWriter;
    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final RoomMemberJoiner roomMemberJoiner;
    private final RoomEntryIdentityService entryIdentity;
    private final Clock clock;

    public RoomJoinService(RoomInviteReader roomInviteReader, RoomInviteWriter roomInviteWriter,
                           RoomReader roomReader, RoomMemberReader roomMemberReader,
                           RoomMemberJoiner roomMemberJoiner, RoomEntryIdentityService entryIdentity, Clock clock) {
        this.roomInviteReader = roomInviteReader;
        this.roomInviteWriter = roomInviteWriter;
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.roomMemberJoiner = roomMemberJoiner;
        this.entryIdentity = entryIdentity;
        this.clock = clock;
    }

    /**
     * 초대코드로 방 입장. room-first 잠금 아래 같은 방 재입장은 멱등 처리하고, 초대코드로 들어온 멤버 수(#513)를
     * 잠긴 초대코드의 maxUses(또는 null 무제한)와 비교한다 — 운영진이 배정한 앱 신청자는 웹 몫 자리를 쓰지 않는다.
     */
    @Transactional
    public JoinRoomResponse join(Long userId, String code) {
        String normalizedCode = InviteCodeGenerator.normalize(code)
                .orElseThrow(InviteCodeNotUsableException::new);

        // 비잠금 조회는 roomId 발견만 담당한다. 모든 배타 락은 room → invite 순서를 지킨다.
        Long discoveredRoomId = roomInviteReader.findRoomIdByCode(normalizedCode)
                .orElseThrow(InviteCodeNotFoundException::new);
        Room room = roomReader.findByIdForUpdate(discoveredRoomId)
                .orElseThrow(RoomNotFoundException::new);
        RoomInvite invite = roomInviteReader.findByCodeForUpdate(normalizedCode)
                .filter(locked -> locked.roomId().equals(room.id()))
                .orElseThrow(InviteCodeNotUsableException::new);
        LocalDateTime now = LocalDateTime.now(clock);

        RoomStatus status = room.statusAt(now);
        if (status == RoomStatus.ENDED || status == RoomStatus.CANCELLED) {
            throw new InviteCodeNotUsableException();
        }

        if (!invite.isUsable(now)) {
            throw new InviteCodeNotUsableException();
        }

        if (roomMemberReader.findByRoomIdAndUserId(room.id(), userId).isPresent()) {
            return new JoinRoomResponse(room.id());
        }

        // 퇴장으로 코드 자리가 다시 열려도 최종 선택이 시작된 방에는 새로 들어올 수 없다(#548).
        if (room.finalSelectionOpensAt() != null && !now.isBefore(room.finalSelectionOpensAt())) {
            throw new InviteCodeNotUsableException();
        }

        if (!invite.hasCapacity(roomMemberReader.countByRoomIdAndEntryPath(room.id(), RoomEntryPath.INVITE))) {
            throw new InviteCodeNotUsableException();
        }

        // PASS 본인확인 게이트(#514): 새 입장에만 적용한다 — 위의 기존 멤버 재입장·코드 거절은 그대로 둔다.
        var identity=entryIdentity.snapshot(room.id(),userId);
        roomMemberJoiner.join(RoomMember.of(room.id(),userId,identity.gender(),identity.birthday(),now));
        roomInviteWriter.save(invite.incrementUsed());
        return new JoinRoomResponse(room.id());
    }

}
