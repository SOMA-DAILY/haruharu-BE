package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MembersResponse;
import org.son.haruharu.domain.room.business.dto.MembersResponse.MemberView;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방 멤버 목록 조회. 항상 노출되는 기본 정보(닉네임·성별·지역·나이)만 담는다.
 * 직업/키/MBTI 등 열쇠 해금 대상 항목은 단건 프로필 조회에서 다룬다.
 */
@Service
@Transactional(readOnly = true)
public class RoomMembersService {

    private final RoomMemberReader roomMemberReader;
    private final UserProfileReader userProfileReader;
    private final Clock clock;

    public RoomMembersService(RoomMemberReader roomMemberReader, UserProfileReader userProfileReader, Clock clock) {
        this.roomMemberReader = roomMemberReader;
        this.userProfileReader = userProfileReader;
        this.clock = clock;
    }

    public MembersResponse getMembers(UUID roomId, Long userId) {
        roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        LocalDate today = LocalDate.now(clock);

        List<RoomMember> members = roomMemberReader.getByRoomId(roomId);
        List<Long> userIds = members.stream().map(RoomMember::userId).toList();
        Map<Long, UserProfile> profiles = userProfileReader.findByUserIdIn(userIds);

        List<MemberView> views = members.stream()
                .map(member -> toView(member, profiles.get(member.userId()), userId, today))
                .toList();

        return new MembersResponse(views);
    }

    private MemberView toView(RoomMember member, UserProfile profile, Long meUserId, LocalDate today) {
        String nickname = profile == null ? null : profile.nickname();
        String location = profile == null ? null : profile.location();
        Integer age = member.birthday() == null
                ? null : Period.between(member.birthday(), today).getYears();

        return new MemberView(member.id(), member.userId().equals(meUserId), nickname, member.gender(), location, age);
    }
}
