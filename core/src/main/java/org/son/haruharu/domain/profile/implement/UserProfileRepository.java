package org.son.haruharu.domain.profile.implement;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserProfileRepository {

    Optional<UserProfile> findByUserId(Long userId);

    List<UserProfile> findByUserIdIn(Collection<Long> userIds);

    UserProfile save(UserProfile userProfile);

    void deleteByUserId(Long userId);

    /** 학교(affiliation)·학과·번호만 바꾼다. 바뀐 행 수(프로필이 없으면 0)를 돌려준다. */
    int updateApplicantContact(Long userId, String school, String department, String phone, LocalDateTime now);
}
