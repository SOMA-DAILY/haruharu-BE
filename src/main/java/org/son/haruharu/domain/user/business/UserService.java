package org.son.haruharu.domain.user.business;

import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.user.business.dto.UserProfileResponse;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserReader userReader;
    private final UserWriter userWriter;
    private final ProfileService profileService;
    private final IdentityService identityService;

    public UserService(
        UserReader userReader,
        UserWriter userWriter,
        ProfileService profileService,
        IdentityService identityService
    ) {
        this.userReader = userReader;
        this.userWriter = userWriter;
        this.profileService = profileService;
        this.identityService = identityService;
    }

    public UserProfileResponse getMyProfile(UUID userId) {
        ProfileResponse profile = profileService.getProfile(userId);
        boolean verified = identityService.isVerified(userId);
        return UserProfileResponse.from(profile, verified);
    }

    @Transactional
    public void withdraw(UUID userId) {
        profileService.deleteAllImages(userId);
        identityService.deleteIdentity(userId);
        User user = userReader.getById(userId);
        userWriter.save(new User(userId, UserStatus.DELETED, user.createdAt()));
    }
}
