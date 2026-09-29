package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MembersResponse;
import org.son.haruharu.domain.room.business.dto.MembersResponse.MemberView;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.profile.implement.ProfileField;
import org.son.haruharu.domain.roomschedule.implement.RevealField;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItemReader;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방 멤버 목록 조회. 닉네임·성별·대표사진·신고용 userId는 항상 노출하고,
 * 지역·나이는 단건 프로필과 동일한 공개 Day(3일차)부터 노출한다 — 본인 항목은 자기 정보이므로 게이트 제외(#214).
 * 직업/키/MBTI 등 나머지 Day 게이트 항목은 단건 프로필 조회에서 다룬다.
 */
@Service
@Transactional(readOnly = true)
public class RoomMembersService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final UserProfileReader userProfileReader;
    private final UserImageReader userImageReader;
    private final MatchResultReader matchResultReader;
    private final S3StoragePort s3StoragePort;
    private final RoomScheduleItemReader roomScheduleItemReader;
    private final Clock clock;

    public RoomMembersService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                              UserProfileReader userProfileReader, UserImageReader userImageReader,
                              MatchResultReader matchResultReader, S3StoragePort s3StoragePort,
                              RoomScheduleItemReader roomScheduleItemReader, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.userProfileReader = userProfileReader;
        this.userImageReader = userImageReader;
        this.matchResultReader = matchResultReader;
        this.s3StoragePort = s3StoragePort;
        this.roomScheduleItemReader = roomScheduleItemReader;
        this.clock = clock;
    }

    public MembersResponse getMembers(Long roomId, Long userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        int currentDay = room.currentDayAt(now);
        // 나이 공개는 프로필 시트 AGE 항목과 같은 판정(#457): 편성 방이면 AGE 공개 시각(취소 행 규칙 포함), 옛 방은 DAY3.
        boolean scheduled = roomScheduleItemReader.hasRevealSchedule(roomId);
        LocalDateTime ageRevealAt = scheduled ? roomScheduleItemReader.findRevealTimes(roomId).get(RevealField.AGE) : null;
        boolean ageOpen = MemberProfileService.isRevealed(ProfileField.AGE, scheduled, ageRevealAt, now, currentDay);

        List<RoomMember> members = roomMemberReader.getByRoomId(roomId);
        List<Long> userIds = members.stream().map(RoomMember::userId).toList();
        Map<Long, UserProfile> profiles = userProfileReader.findByUserIdIn(userIds);
        Map<Long, String> imageUrls = userImageReader.getRepresentativeImageUrls(userIds);
        // 최종매칭은 1:1이라 내 매칭 상대는 최대 1명 — 한 번 조회해 루프에서 in-memory 비교한다(멤버당 isMatched N+1 방지).
        Long matchedPartnerMemberId = matchResultReader.findByMember(me.id())
                .map(match -> match.memberAId().equals(me.id()) ? match.memberBId() : match.memberAId())
                .orElse(null);

        List<MemberView> views = members.stream()
                .map(member -> toView(member, matchedPartnerMemberId,
                        profiles.get(member.userId()), imageUrls.get(member.userId()),
                        userId, now, currentDay, ageOpen))
                .toList();

        return new MembersResponse(views);
    }

    private MemberView toView(RoomMember member, Long matchedPartnerMemberId, UserProfile profile, String imageUrl,
                              Long meUserId, LocalDateTime now, int currentDay, boolean ageOpen) {
        boolean self = member.userId().equals(meUserId);
        // 방 목록의 본인 이름도 가명으로 표시한다. 상호매칭 상대만 프로필 이름을 공개한다.
        boolean nameRevealed = !self && member.id().equals(matchedPartnerMemberId);
        String displayName = nameRevealed
                ? (profile == null ? null : profile.nickname())
                : member.displayPseudonym();
        // 지역·나이 공개 기준(#214). 지역은 #457 에서 단건 프로필 항목에서 빠졌지만 목록은 기존 DAY3 규칙을 그대로 쓴다.
        boolean locationRevealed = self || currentDay >= MemberProfileService.LOCATION_REVEAL_DAY;
        boolean ageRevealed = self || ageOpen;
        String location = !locationRevealed || profile == null ? null : profile.location();
        Integer age = ageRevealed ? MemberProfileService.ageOf(profile, member, now) : null;
        // 대표사진은 presigned GET URL로 항상 반환하고 블러 처리는 클라이언트 담당(단건 프로필과 동일 정책)
        String profileImageUrl = s3StoragePort.createPresignedImageGetUrl(imageUrl);

        return new MemberView(member.id(), member.userId(), self,
                displayName, nameRevealed, member.gender(), location, age, profileImageUrl);
    }
}
