package org.son.haruharu.domain.profile.business;

import org.son.haruharu.domain.profile.business.command.UpdateRoomProfileCommand;
import org.son.haruharu.domain.profile.business.dto.RoomProfileResponse;
import org.son.haruharu.domain.profile.implement.RoomProfile;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 방 참가 전 온보딩 프로필(#455). 계정 프로필(user_profiles)의 4칸(birth_year, mbti, department, into_now)만 읽고 쓴다.
 * 기존 PUT /users/me/profile 과 같은 행이지만 서로의 칸을 지우지 않는다(UpdateProfileCommand.applyTo 가 이 3칸을 보존).
 */
@Service
@Transactional(readOnly = true)
public class RoomProfileService {

    private final UserProfileReader userProfileReader;
    private final UserProfileWriter userProfileWriter;
    private final Clock clock;

    public RoomProfileService(UserProfileReader userProfileReader, UserProfileWriter userProfileWriter, Clock clock) {
        this.userProfileReader = userProfileReader;
        this.userProfileWriter = userProfileWriter;
        this.clock = clock;
    }

    /** 프로필 행이 없어도 404 가 아니라 빈 값(completed=false)으로 답한다 — 앱은 404 를 "서버 미배포"로 읽는다. */
    public RoomProfileResponse getRoomProfile(Long userId) {
        UserProfile profile = userProfileReader.findByUserId(userId).orElseGet(() -> UserProfile.empty(userId));
        return RoomProfileResponse.of(RoomProfile.of(profile), currentYear());
    }

    @Transactional
    public RoomProfileResponse updateRoomProfile(Long userId, UpdateRoomProfileCommand command) {
        int year = currentYear();
        RoomProfile validated = RoomProfile.validated(
                command.birthYear(), command.mbti(), command.department(), command.intoNow(), year);
        UserProfile existing = userProfileReader.findByUserId(userId).orElseGet(() -> UserProfile.empty(userId));
        UserProfile saved = userProfileWriter.save(existing.withRoomProfile(
                validated.birthYear(), validated.mbti(), validated.department(), validated.intoNow()));
        return RoomProfileResponse.of(RoomProfile.of(saved), year);
    }

    private int currentYear() {
        return LocalDate.now(clock).getYear();
    }
}
