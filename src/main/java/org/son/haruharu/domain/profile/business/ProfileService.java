package org.son.haruharu.domain.profile.business;

import org.son.haruharu.domain.profile.business.command.UpdateImagesCommand;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserImageWriter;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    private final UserProfileReader userProfileReader;
    private final UserProfileWriter userProfileWriter;
    private final UserImageReader userImageReader;
    private final UserImageWriter userImageWriter;

    public ProfileService(
        UserProfileReader userProfileReader,
        UserProfileWriter userProfileWriter,
        UserImageReader userImageReader,
        UserImageWriter userImageWriter
    ) {
        this.userProfileReader = userProfileReader;
        this.userProfileWriter = userProfileWriter;
        this.userImageReader = userImageReader;
        this.userImageWriter = userImageWriter;
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileCommand command) {
        UserProfile existing = userProfileReader.getByUserId(userId);
        UserProfile updated = command.applyTo(existing);
        UserProfile saved = userProfileWriter.save(updated);
        List<UserImage> images = userImageReader.getAllByUserId(userId);
        return ProfileResponse.from(saved, images);
    }

    @Transactional
    public void updateImages(UUID userId, UpdateImagesCommand command) {
        userImageWriter.deleteAllByUserId(userId);
        List<String> urls = command.imageUrls();
        for (int i = 0; i < urls.size(); i++) {
            userImageWriter.save(new UserImage(UUID.randomUUID(), userId, urls.get(i), i));
        }
    }
}
