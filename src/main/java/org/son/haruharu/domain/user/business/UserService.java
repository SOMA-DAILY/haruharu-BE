package org.son.haruharu.domain.user.business;

import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserImageWriter;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.son.haruharu.domain.user.business.command.UpdateImagesCommand;
import org.son.haruharu.domain.user.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.user.business.command.VerifyIdentityCommand;
import org.son.haruharu.domain.user.business.dto.UserProfileResponse;
import org.son.haruharu.domain.user.business.exception.UserAlreadyVerifiedException;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserReader userReader;
    private final UserWriter userWriter;
    private final UserProfileReader userProfileReader;
    private final UserProfileWriter userProfileWriter;
    private final UserImageReader userImageReader;
    private final UserImageWriter userImageWriter;
    private final UserIdentityReader userIdentityReader;
    private final UserIdentityWriter userIdentityWriter;
    private final PassVerificationPort passVerificationPort;

    public UserService(
        UserReader userReader,
        UserWriter userWriter,
        UserProfileReader userProfileReader,
        UserProfileWriter userProfileWriter,
        UserImageReader userImageReader,
        UserImageWriter userImageWriter,
        UserIdentityReader userIdentityReader,
        UserIdentityWriter userIdentityWriter,
        PassVerificationPort passVerificationPort
    ) {
        this.userReader = userReader;
        this.userWriter = userWriter;
        this.userProfileReader = userProfileReader;
        this.userProfileWriter = userProfileWriter;
        this.userImageReader = userImageReader;
        this.userImageWriter = userImageWriter;
        this.userIdentityReader = userIdentityReader;
        this.userIdentityWriter = userIdentityWriter;
        this.passVerificationPort = passVerificationPort;
    }

    public UserProfileResponse getMyProfile(UUID userId) {
        UserProfile profile = userProfileReader.getByUserId(userId);
        List<UserImage> images = userImageReader.getAllByUserId(userId);
        boolean verified = userIdentityReader.existsByUserId(userId);
        return UserProfileResponse.from(profile, images, verified);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileCommand command) {
        UserProfile existing = userProfileReader.getByUserId(userId);
        UserProfile updated = command.applyTo(existing);
        UserProfile saved = userProfileWriter.save(updated);
        List<UserImage> images = userImageReader.getAllByUserId(userId);
        boolean verified = userIdentityReader.existsByUserId(userId);
        return UserProfileResponse.from(saved, images, verified);
    }

    @Transactional
    public void updateImages(UUID userId, UpdateImagesCommand command) {
        userImageWriter.deleteAllByUserId(userId);
        List<String> urls = command.imageUrls();
        for (int i = 0; i < urls.size(); i++) {
            userImageWriter.save(new UserImage(UUID.randomUUID(), userId, urls.get(i), i));
        }
    }

    @Transactional
    public void verifyIdentity(UUID userId, VerifyIdentityCommand command) {
        if (userIdentityReader.existsByUserId(userId)) {
            throw new UserAlreadyVerifiedException();
        }
        PassVerificationResult result = passVerificationPort.verify(command.passToken());
        UserIdentity identity = new UserIdentity(
            UUID.randomUUID(), userId,
            result.ci(), result.di(),
            result.phoneNumber(), result.birthday(), result.gender(),
            result.userName(), result.operator(), result.isForeigner(),
            LocalDateTime.now()
        );
        userIdentityWriter.save(identity);
    }

    @Transactional
    public void withdraw(UUID userId) {
        userImageWriter.deleteAllByUserId(userId);
        userIdentityWriter.deleteByUserId(userId);
        User user = userReader.getById(userId);
        userWriter.save(new User(userId, UserStatus.DELETED, user.createdAt()));
    }
}
