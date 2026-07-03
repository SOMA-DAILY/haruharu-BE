package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.room.business.dto.MatchResultResponse;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 내 매칭 결과 조회. 상호선택이 성사된 매칭이 없으면 404. */
@Service
@Transactional(readOnly = true)
public class MatchResultService {

    private final RoomMemberReader roomMemberReader;
    private final MatchResultReader matchResultReader;

    public MatchResultService(RoomMemberReader roomMemberReader, MatchResultReader matchResultReader) {
        this.roomMemberReader = roomMemberReader;
        this.matchResultReader = matchResultReader;
    }

    public MatchResultResponse getMatchResult(UUID roomId, Long userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        MatchResult match = matchResultReader.getByMember(me.id());

        UUID partnerMemberId = match.memberAId().equals(me.id()) ? match.memberBId() : match.memberAId();
        return new MatchResultResponse(partnerMemberId, match.matchedAt());
    }
}
