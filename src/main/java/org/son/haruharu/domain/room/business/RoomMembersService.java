package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MembersResponse;
import org.son.haruharu.domain.room.business.dto.MembersResponse.MemberView;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

/** 방 멤버 목록 조회. 기본 프로필(직업/학교·키·MBTI)은 Day3부터 공개한다. */
@Service
@Transactional(readOnly = true)
public class RoomMembersService {

    /** 직업/학교·키·MBTI 등 기본 프로필이 공개되는 Day(와이어 [Day3] 게이트). */
    private static final int PROFILE_REVEAL_DAY = 3;

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final UserProfileReader userProfileReader;
    private final Clock clock;

    public RoomMembersService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                              UserProfileReader userProfileReader, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.userProfileReader = userProfileReader;
        this.clock = clock;
    }

    public MembersResponse getMembers(UUID roomId, UUID userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);

        boolean revealed = room.currentDay() >= PROFILE_REVEAL_DAY;
        LocalDate today = LocalDate.now(clock);

        List<MemberView> views = roomMemberReader.getByRoomId(roomId).stream()
                .map(member -> toView(member, userId, revealed, today))
                .toList();

        return new MembersResponse(me.pointSum(), views);
    }

    private MemberView toView(RoomMember member, UUID meUserId, boolean revealed, LocalDate today) {
        UserProfile profile = userProfileReader.findByUserId(member.userId()).orElse(null);

        String nickname = profile == null ? null : profile.nickname();
        String location = profile == null ? null : profile.location();
        Integer age = (profile == null || profile.birthday() == null)
                ? null : Period.between(profile.birthday(), today).getYears();

        // Day3 게이트
        String affiliation = revealed && profile != null ? profile.affiliation() : null;
        Integer height = revealed && profile != null ? profile.height() : null;
        String mbti = revealed && profile != null ? profile.mbti() : null;

        return new MemberView(
                member.id(), member.userId().equals(meUserId),
                nickname, member.gender(), location, age, affiliation, height, mbti);
    }
}
