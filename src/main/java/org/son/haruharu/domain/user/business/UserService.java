package org.son.haruharu.domain.user.business;

import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserImageWriter;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.user.business.dto.UserProfileResponse;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserReader userReader;
    private final UserWriter userWriter;
    private final UserProfileReader userProfileReader;
    private final UserImageReader userImageReader;
    private final UserIdentityReader userIdentityReader;
    private final UserImageWriter userImageWriter;
    private final UserIdentityWriter userIdentityWriter;

    public UserService(
        UserReader userReader,
        UserWriter userWriter,
        UserProfileReader userProfileReader,
        UserImageReader userImageReader,
        UserIdentityReader userIdentityReader,
        UserImageWriter userImageWriter,
        UserIdentityWriter userIdentityWriter
    ) {
        this.userReader = userReader;
        this.userWriter = userWriter;
        this.userProfileReader = userProfileReader;
        this.userImageReader = userImageReader;
        this.userIdentityReader = userIdentityReader;
        this.userImageWriter = userImageWriter;
        this.userIdentityWriter = userIdentityWriter;
    }

    public UserProfileResponse getMyProfile(UUID userId) {
        UserProfile profile = userProfileReader.getByUserId(userId);
        List<UserImage> images = userImageReader.getAllByUserId(userId);
        boolean verified = userIdentityReader.existsByUserId(userId);
        return UserProfileResponse.from(profile, images, verified);
    }

    @Transactional
    public void withdraw(UUID userId) {
        userImageWriter.deleteAllByUserId(userId);
        userIdentityWriter.deleteByUserId(userId);
        User user = userReader.getById(userId);
        userWriter.save(new User(userId, UserStatus.DELETED, user.createdAt()));
    }
}
