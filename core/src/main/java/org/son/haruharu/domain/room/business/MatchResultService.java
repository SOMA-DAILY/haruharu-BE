package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MatchResultResponse;
import org.son.haruharu.domain.room.business.dto.PartnerMemberRef;
import org.son.haruharu.domain.room.business.dto.PendingMatchResultResponse;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.room.storage.exception.MatchResultNotFoundException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 매칭 결과 조회. 상호선택이 성사된 매칭이 없으면 404. */
@Service
@Transactional(readOnly = true)
public class MatchResultService {

    private final RoomMemberReader roomMemberReader;
    private final MatchResultReader matchResultReader;
    private final ChatRoomReader chatRoomReader;
    private final UserProfileReader userProfileReader;
    private final UserImageReader userImageReader;
    private final S3StoragePort s3StoragePort;
    private final RoomReader roomReader;
    private final FinalSelectionResolver finalSelectionResolver;
    private final Clock clock;

    public MatchResultService(RoomMemberReader roomMemberReader, MatchResultReader matchResultReader,
                              ChatRoomReader chatRoomReader, UserProfileReader userProfileReader,
                              UserImageReader userImageReader, S3StoragePort s3StoragePort,
                              RoomReader roomReader, FinalSelectionResolver finalSelectionResolver, Clock clock) {
        this.roomMemberReader = roomMemberReader;
        this.matchResultReader = matchResultReader;
        this.chatRoomReader = chatRoomReader;
        this.userProfileReader = userProfileReader;
        this.userImageReader = userImageReader;
        this.s3StoragePort = s3StoragePort;
        this.roomReader = roomReader;
        this.finalSelectionResolver = finalSelectionResolver;
        this.clock = clock;
    }

    public MatchResultResponse getMatchResult(Long roomId, Long userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        // 동시공개(#343): result_at 이전엔 결과를 공개하지 않는다(발표 전 조기 노출 방지).
        Room room = roomReader.findById(roomId).orElseThrow(RoomNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        if (room.resultAt() != null && now.isBefore(room.resultAt())) {
            throw new MatchResultNotFoundException();
        }
        MatchResult match = resolveThenGet(roomId, me.id(), now);

        Long partnerMemberId = match.memberAId().equals(me.id()) ? match.memberBId() : match.memberAId();
        RoomMember partner = roomMemberReader.getById(partnerMemberId);

        // 결과 화면 "채팅 시작하기" 직행: 자동 생성된 채팅방 id(아직 없으면 null).
        Long chatRoomId = chatRoomReader.findChatRoomIdByMatchResultId(match.id()).orElse(null);
        // 상대 프로필 요약(닉네임·대표사진 presigned GET URL). 이미지가 없으면 null.
        String nickname = userProfileReader.findByUserId(partner.userId()).map(UserProfile::nickname).orElse(null);
        String profileImageUrl = s3StoragePort.createPresignedImageGetUrl(
                userImageReader.getRepresentativeImageUrl(partner.userId()));

        return new MatchResultResponse(partnerMemberId, match.matchedAt(), chatRoomId,
                new MatchResultResponse.PartnerSummary(nickname, profileImageUrl));
    }

    /**
     * 확인 대기 중인 최근 매칭 결과(홈 자동 노출용). 유저의 가장 최근 방(joinedAt 기준)이
     * 결과 발표(Day3 22:00)를 지났으면 MATCHED/FAILED를 담아 반환하고, 발표 전·미시작(PENDING)·
     * 방 이력 없음이면 empty. 새 방에 배정되면 최근 방이 바뀌므로 이전 결과는 자연히 노출이 끝난다.
     */
    public Optional<PendingMatchResultResponse> findPendingResult(Long userId) {
        List<RoomMember> memberships = roomMemberReader.findByUserId(userId);
        Optional<RoomMember> latest = memberships.stream()
                .max(Comparator.comparing(RoomMember::joinedAt).thenComparing(RoomMember::id));
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        RoomMember me = latest.get();
        Room room = roomReader.findById(me.roomId()).orElse(null);
        // 미시작(PENDING, startedAt 없음)은 발표될 결과가 없다 — 새 매칭 확정 직후가 여기 해당.
        if (room == null || room.startedAt() == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime announcedAt = room.announcedAt();
        if (now.isBefore(announcedAt)) {
            return Optional.empty();
        }
        // 발표 시각 도달: sweep 을 기다리지 않고 결과를 확정한다(멱등 resolve). MATCHED/FAILED 판정이
        // 배치 지연으로 잘못 나오지 않게 조회 직전에 보장한다.
        MatchResult match = resolveThenFind(room.id(), me.id(), now).orElse(null);
        if (match == null) {
            return Optional.of(PendingMatchResultResponse.failed(room.id(), announcedAt));
        }
        return Optional.of(matchedResult(room.id(), me, match, announcedAt));
    }

    /**
     * 매칭이 아직 없으면 최종선택을 즉시 resolve 한 뒤 다시 조회한다. reconciler sweep(최대 5분 지연)이나
     * 구 모델 방(sweep 대상 아님)에서도 발표 시각 조회에 결과가 보장되게 하는 lazy 경로다.
     * 이미 매칭된 유저는 resolve 를 건너뛴다(빠른 경로). resolveForRoom 은 자체 트랜잭션·멱등이다.
     */
    private MatchResult resolveThenGet(Long roomId, Long memberId, LocalDateTime now) {
        return resolveThenFind(roomId, memberId, now)
                .orElseThrow(MatchResultNotFoundException::new);
    }

    private Optional<MatchResult> resolveThenFind(Long roomId, Long memberId, LocalDateTime now) {
        Optional<MatchResult> found = matchResultReader.findByMember(memberId);
        if (found.isPresent()) {
            return found;
        }
        finalSelectionResolver.resolveForRoom(roomId, now);
        return matchResultReader.findByMember(memberId);
    }

    private PendingMatchResultResponse matchedResult(Long roomId, RoomMember me, MatchResult match,
                                                     LocalDateTime announcedAt) {
        Long partnerMemberId = match.memberAId().equals(me.id()) ? match.memberBId() : match.memberAId();
        RoomMember partner = roomMemberReader.getById(partnerMemberId);
        String nickname = userProfileReader.findByUserId(partner.userId()).map(UserProfile::nickname).orElse(null);
        Long chatRoomId = chatRoomReader.findChatRoomIdByMatchResultId(match.id()).orElse(null);
        return new PendingMatchResultResponse(roomId, PendingMatchResultResponse.Outcome.MATCHED,
                chatRoomId, nickname, announcedAt);
    }

    /**
     * matchResultId와 요청자 userId로 매칭 상대의 방 멤버(roomId·partnerMemberId·partnerUserId)를 해석한다.
     * 채팅 등 타 도메인이 상대 프로필·영상을 조회할 때 사용한다. 요청자가 이 매칭의 당사자가 아니면 403.
     */
    public PartnerMemberRef resolvePartner(Long matchResultId, Long viewerUserId) {
        MatchResult match = matchResultReader.getById(matchResultId);
        RoomMember a = roomMemberReader.getById(match.memberAId());
        RoomMember b = roomMemberReader.getById(match.memberBId());

        RoomMember partner;
        if (a.userId().equals(viewerUserId)) {
            partner = b;
        } else if (b.userId().equals(viewerUserId)) {
            partner = a;
        } else {
            throw new NotRoomMemberException(); // 요청자가 이 매칭의 당사자가 아님
        }
        return new PartnerMemberRef(partner.roomId(), partner.id(), partner.userId());
    }
}
