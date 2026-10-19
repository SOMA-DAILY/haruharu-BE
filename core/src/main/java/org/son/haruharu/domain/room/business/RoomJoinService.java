package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.JoinRoomResponse;
import org.son.haruharu.domain.room.business.exception.IdentityRequiredException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대코드로 방 입장(#312). 앱 서버 전용 유스케이스다 — 어드민은 코드 발급(RoomInviteService)만 쓰고
 * 본인확인(identity) 빈도 두지 않는다.
 * <p>
 * {@code haruharu.room.join-identity-required}(기본 false)가 켜지면 PASS 본인확인을 하지 않은 계정의
 * 새 입장을 403 IDENTITY_REQUIRED 로 막는다(#514). 계정당 한 번 인증하면 이후 입장은 그대로 된다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class RoomJoinService {

    private final RoomInviteReader roomInviteReader;
    private final RoomInviteWriter roomInviteWriter;
    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final RoomMemberJoiner roomMemberJoiner;
    private final UserProfileReader userProfileReader;
    private final UserIdentityReader userIdentityReader;
    private final Clock clock;
    private final boolean identityRequired;

    public RoomJoinService(RoomInviteReader roomInviteReader, RoomInviteWriter roomInviteWriter,
                           RoomReader roomReader, RoomMemberReader roomMemberReader,
                           RoomMemberJoiner roomMemberJoiner, UserProfileReader userProfileReader,
                           UserIdentityReader userIdentityReader, Clock clock,
                           @Value("${haruharu.room.join-identity-required:false}") boolean identityRequired) {
        this.roomInviteReader = roomInviteReader;
        this.roomInviteWriter = roomInviteWriter;
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.roomMemberJoiner = roomMemberJoiner;
        this.userProfileReader = userProfileReader;
        this.userIdentityReader = userIdentityReader;
        this.clock = clock;
        this.identityRequired = identityRequired;
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

        if (!invite.hasCapacity(roomMemberReader.countByRoomIdAndEntryPath(room.id(), RoomEntryPath.INVITE))) {
            throw new InviteCodeNotUsableException();
        }

        // PASS 본인확인 게이트(#514): 새 입장에만 적용한다 — 위의 기존 멤버 재입장·코드 거절은 그대로 둔다.
        Optional<UserIdentity> identity = userIdentityReader.findByUserId(userId);
        if (identityRequired && identity.isEmpty()) {
            throw new IdentityRequiredException();
        }
        // 성별·생년 스냅샷: PASS 가 있으면 PASS 값(#514), 없을 때만 프로필 성별(#392).
        // PASS 성별이 남녀가 아니면 자기신고 프로필로 대신하지 않고 null 로 둔다(운영진이 부여).
        String gender = identity.isPresent()
                ? passGender(identity.get(), room.id())
                : profileGender(userId);
        LocalDate birthday = identity.map(UserIdentity::birthday).orElse(null);
        // 가명 배정(#393)·저장·입장 이벤트는 앱 신청자 배정과 같은 공통 경로를 쓴다(#513).
        // 코드 사용 횟수는 그 뒤에 올린다 — 입장 이벤트 리스너(그룹채팅 등록)는 usedCount 를 읽지 않고, 실패하면 함께 롤백된다.
        roomMemberJoiner.join(RoomMember.of(room.id(), userId, gender, birthday, now));
        roomInviteWriter.save(invite.incrementUsed());
        return new JoinRoomResponse(room.id());
    }

    /** PASS 성별. 남녀가 아니면 null — 가명·성별 질문 대상에서 빠지므로 운영진이 부여하도록 경고를 남긴다. */
    private String passGender(UserIdentity identity, Long roomId) {
        if (RoomMember.SUPPORTED_GENDERS.contains(identity.gender())) {
            return identity.gender();
        }
        log.warn("PASS 성별을 방 멤버 성별로 쓸 수 없어 비워 둔다 — 운영진 성별 부여 필요. roomId={}, userId={}",
                roomId, identity.userId());
        return null;
    }

    /** 프로필 gender. 프로필·gender 미설정이면 null(운영진이 추후 부여). */
    private String profileGender(Long userId) {
        return userProfileReader.findByUserId(userId)
                .map(UserProfile::gender)
                .map(Gender::name)
                .orElse(null);
    }
}
