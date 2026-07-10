package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MatchResultResponse;
import org.son.haruharu.domain.room.business.dto.PartnerMemberRef;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
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

    public MatchResultService(RoomMemberReader roomMemberReader, MatchResultReader matchResultReader,
                              ChatRoomReader chatRoomReader, UserProfileReader userProfileReader,
                              UserImageReader userImageReader, S3StoragePort s3StoragePort) {
        this.roomMemberReader = roomMemberReader;
        this.matchResultReader = matchResultReader;
        this.chatRoomReader = chatRoomReader;
        this.userProfileReader = userProfileReader;
        this.userImageReader = userImageReader;
        this.s3StoragePort = s3StoragePort;
    }

    public MatchResultResponse getMatchResult(Long roomId, Long userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        MatchResult match = matchResultReader.getByMember(me.id());

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
