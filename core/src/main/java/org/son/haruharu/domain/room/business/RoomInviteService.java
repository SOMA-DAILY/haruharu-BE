package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;

import org.son.haruharu.domain.room.business.dto.JoinRoomResponse;
import org.son.haruharu.domain.room.business.exception.AlreadyJoinedRoomException;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotFoundException;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotUsableException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomInviteReader;
import org.son.haruharu.domain.room.implement.RoomInviteWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomInviteService {

    private final RoomInviteReader roomInviteReader;
    private final RoomInviteWriter roomInviteWriter;
    private final RoomReader roomReader;
    private final RoomService roomService;
    private final RoomMemberWriter roomMemberWriter;
    private final Clock clock;

    public RoomInviteService(RoomInviteReader roomInviteReader, RoomInviteWriter roomInviteWriter,
                             RoomReader roomReader, RoomService roomService,
                             RoomMemberWriter roomMemberWriter, Clock clock) {
        this.roomInviteReader = roomInviteReader;
        this.roomInviteWriter = roomInviteWriter;
        this.roomReader = roomReader;
        this.roomService = roomService;
        this.roomMemberWriter = roomMemberWriter;
        this.clock = clock;
    }

    /**
     * 초대코드로 방 입장(#312). 코드 유효성·방 상태·정원(maxUses)을 검증하고, 이미 참여 중인 방이 있으면 거부한 뒤
     * room_member를 추가한다. gender는 온보딩 미수집이라 null로 입장하며 이후 운영진/PASS 인증이 채운다. 반환은 roomId.
     */
    @Transactional
    public JoinRoomResponse join(Long userId, String code) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 정원(usedCount) 갱신 경쟁 방지 — 코드 행에 비관적 락(#312 리뷰 반영).
        RoomInvite invite = roomInviteReader.findByCodeForUpdate(code)
                .orElseThrow(InviteCodeNotFoundException::new);
        if (!invite.isUsable(now)) {
            throw new InviteCodeNotUsableException();
        }
        Room room = roomReader.findById(invite.roomId())
                .orElseThrow(RoomNotFoundException::new);
        RoomStatus status = room.statusAt(now);
        if (status == RoomStatus.ENDED || status == RoomStatus.CANCELLED) {
            throw new InviteCodeNotUsableException(); // 종료·취소된 방은 입장 불가
        }
        // 유저는 동시에 활성 방 1개만 — 이미 참여 중인(종료 안 된) 방이 있으면 거부(대상 방 포함).
        if (roomService.hasCurrentRoom(userId)) {
            throw new AlreadyJoinedRoomException();
        }
        roomMemberWriter.save(RoomMember.of(room.id(), userId, null, null, now));
        roomInviteWriter.save(invite.incrementUsed());
        return new JoinRoomResponse(room.id());
    }

    /** 코드 발급(#312). HTTP 진입점은 admin 세션 — 여기선 core 도메인 로직(admin·테스트가 재사용). */
    @Transactional
    public RoomInvite issue(Long roomId, String code, Integer maxUses, LocalDateTime expiresAt, String createdBy) {
        return roomInviteWriter.save(RoomInvite.issue(roomId, code, maxUses, expiresAt, createdBy));
    }
}
