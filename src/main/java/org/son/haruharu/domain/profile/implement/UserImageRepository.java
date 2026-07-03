package org.son.haruharu.domain.profile.implement;

import java.util.List;

public interface UserImageRepository {

    List<UserImage> findAllByUserId(Long userId);

    UserImage save(UserImage userImage);

    void deleteById(Long id);

    void deleteAllByUserId(Long userId);
}
