package org.son.haruharu.domain.profile.implement;

import java.util.List;
import java.util.UUID;

public interface UserImageRepository {

    List<UserImage> findAllByUserId(UUID userId);

    UserImage save(UserImage userImage);

    void deleteById(UUID id);

    void deleteAllByUserId(UUID userId);
}
