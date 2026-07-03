package org.son.haruharu.domain.user.business;

import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.identity.implement.VerificationStatus;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.user.business.dto.UserProfileResponse;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserReader userReader;
    private final UserWriter userWriter;
    private final ProfileService profileService;
    private final IdentityService identityService;
    private final Clock clock;

    public UserService(
        UserReader userReader,
        UserWriter userWriter,
        ProfileService profileService,
        IdentityService identityService,
        Clock clock
    ) {
        this.userReader = userReader;
        this.userWriter = userWriter;
        this.profileService = profileService;
        this.identityService = identityService;
        this.clock = clock;
    }

    public UserProfileResponse getMyProfile(Long userId) {
        ProfileResponse profile = profileService.getProfile(userId);
        Optional<VerifiedIdentity> verified = identityService.findVerified(userId);
        String gender = verified.map(VerifiedIdentity::gender).orElse(null);
        Integer age = verified.map(VerifiedIdentity::birthday)
            .map(birthday -> Period.between(birthday, LocalDate.now(clock)).getYears())
            .orElse(null);
        VerificationStatus status = VerificationStatus.of(verified.isPresent());
        return UserProfileResponse.from(userId, profile, gender, age, status.name());
    }

    @Transactional
    public void withdraw(Long userId) {
        profileService.deleteAllImages(userId);
        identityService.deleteIdentity(userId);
        User user = userReader.getById(userId);
        userWriter.save(new User(userId, UserStatus.DELETED, user.createdAt()));
    }
}
