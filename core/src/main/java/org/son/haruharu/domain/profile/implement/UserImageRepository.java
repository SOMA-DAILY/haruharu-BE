package org.son.haruharu.domain.profile.implement;

import java.util.Collection;
import java.util.List;

public interface UserImageRepository {

    List<UserImage> findAllByUserId(Long userId);

    /** 여러 유저의 이미지를 userId·sortedOrder 오름차순으로 일괄 조회한다. */
    List<UserImage> findAllByUserIdIn(Collection<Long> userIds);

    UserImage save(UserImage userImage);

    void deleteById(Long id);

    void deleteAllByUserId(Long userId);
}
