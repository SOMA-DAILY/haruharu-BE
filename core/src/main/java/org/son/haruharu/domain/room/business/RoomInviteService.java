package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.domain.room.business.dto.JoinRoomResponse;
import org.son.haruharu.domain.room.business.event.RoomMemberJoinedEvent;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotFoundException;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotUsableException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.InviteCodeGenerator;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomInviteReader;
import org.son.haruharu.domain.room.implement.RoomInviteStatus;
import org.son.haruharu.domain.room.implement.RoomInviteWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomInviteService {

    private final RoomInviteReader roomInviteReader;
    private final RoomInviteWriter roomInviteWriter;
    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final RoomMemberWriter roomMemberWriter;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RoomInviteService(RoomInviteReader roomInviteReader, RoomInviteWriter roomInviteWriter,
                             RoomReader roomReader, RoomMemberReader roomMemberReader,
                             RoomMemberWriter roomMemberWriter, InviteCodeGenerator inviteCodeGenerator,
                             ApplicationEventPublisher eventPublisher, Clock clock) {
        this.roomInviteReader = roomInviteReader;
        this.roomInviteWriter = roomInviteWriter;
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.roomMemberWriter = roomMemberWriter;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 초대코드로 방 입장. room-first 잠금 아래 같은 방 재입장은 멱등 처리하고 active membership을
     * 잠긴 초대코드의 maxUses(또는 null 무제한)와 비교한다.
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

        if (!invite.hasCapacity(roomMemberReader.countByRoomId(room.id()))) {
            throw new InviteCodeNotUsableException();
        }

        roomMemberWriter.save(RoomMember.of(room.id(), userId, null, null, now));
        roomInviteWriter.save(invite.incrementUsed());
        eventPublisher.publishEvent(new RoomMemberJoinedEvent(room.id(), userId, now));
        return new JoinRoomResponse(room.id());
    }

    /** 코드 발급(#312). HTTP 진입점은 admin 세션 — 여기선 core 도메인 로직(admin·테스트가 재사용). */
    @Transactional
    public RoomInvite issue(Long roomId, String code, Integer maxUses, LocalDateTime expiresAt, String createdBy) {
        return roomInviteWriter.save(RoomInvite.issue(roomId, code, maxUses, expiresAt, createdBy));
    }

    /** 코드 자동생성 발급(#318). 코드 문자열은 InviteCodeGenerator 가 만든다(운영진이 직접 짓지 않는다). */
    @Transactional
    public RoomInvite issueFor(Long roomId, Integer maxUses, LocalDateTime expiresAt, String createdBy) {
        return issue(roomId, inviteCodeGenerator.generate(), maxUses, expiresAt, createdBy);
    }

    /**
     * 코드 재발급(#318). 방당 ACTIVE 코드 1개 규약을 지키기 위해 기존 ACTIVE 코드를 먼저 EXPIRED 로 닫고
     * 새 코드를 발급한다. 이미 닫힌 코드는 건드리지 않는다(불필요한 UPDATE 방지).
     */
    @Transactional
    public RoomInvite reissueFor(Long roomId, Integer maxUses, LocalDateTime expiresAt, String createdBy) {
        roomReader.findByIdForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
        roomInviteReader.findByRoomId(roomId).stream()
                .filter(invite -> invite.status() == RoomInviteStatus.ACTIVE)
                .forEach(invite -> roomInviteWriter.save(invite.expired()));
        return issueFor(roomId, maxUses, expiresAt, createdBy);
    }

    /** 방의 코드 이력(최신순). 어드민 조회용(#318). */
    public List<RoomInvite> getByRoomId(Long roomId) {
        return roomInviteReader.findByRoomId(roomId);
    }
}
