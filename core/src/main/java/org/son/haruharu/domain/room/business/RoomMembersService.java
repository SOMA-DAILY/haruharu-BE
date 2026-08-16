package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Map;

import org.son.haruharu.domain.profile.implement.ProfileField;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MembersResponse;
import org.son.haruharu.domain.room.business.dto.MembersResponse.MemberView;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
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
    private final S3StoragePort s3StoragePort;
    private final Clock clock;

    public RoomMembersService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                              UserProfileReader userProfileReader, UserImageReader userImageReader,
                              S3StoragePort s3StoragePort, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.userProfileReader = userProfileReader;
        this.userImageReader = userImageReader;
        this.s3StoragePort = s3StoragePort;
        this.clock = clock;
    }

    public MembersResponse getMembers(Long roomId, Long userId) {
        roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        int currentDay = room.currentDayAt(now);

        List<RoomMember> members = roomMemberReader.getByRoomId(roomId);
        List<Long> userIds = members.stream().map(RoomMember::userId).toList();
        Map<Long, UserProfile> profiles = userProfileReader.findByUserIdIn(userIds);
        Map<Long, String> imageUrls = userImageReader.getRepresentativeImageUrls(userIds);

        List<MemberView> views = members.stream()
                .map(member -> toView(member, profiles.get(member.userId()), imageUrls.get(member.userId()),
                        userId, now.toLocalDate(), currentDay))
                .toList();

        return new MembersResponse(views);
    }

    private MemberView toView(RoomMember member, UserProfile profile, String imageUrl, Long meUserId,
                              LocalDate today, int currentDay) {
        boolean self = member.userId().equals(meUserId);
        String nickname = profile == null ? null : profile.nickname();
        // 지역·나이 공개 기준은 단건 프로필(ProfileField.LOCATION / AGE_REVEAL_DAY)과 동일 소스 — 목록으로 우회 노출되지 않게 한다(#214)
        boolean locationRevealed = self || currentDay >= ProfileField.LOCATION.availableFromDay();
        boolean ageRevealed = self || currentDay >= MemberProfileService.AGE_REVEAL_DAY;
        String location = !locationRevealed || profile == null ? null : profile.location();
        Integer age = !ageRevealed || member.birthday() == null
                ? null : Period.between(member.birthday(), today).getYears();
        // 대표사진은 presigned GET URL로 항상 반환하고 블러 처리는 클라이언트 담당(단건 프로필과 동일 정책)
        String profileImageUrl = s3StoragePort.createPresignedImageGetUrl(imageUrl);

        return new MemberView(member.id(), member.userId(), self,
                nickname, member.gender(), location, age, profileImageUrl);
    }
}
