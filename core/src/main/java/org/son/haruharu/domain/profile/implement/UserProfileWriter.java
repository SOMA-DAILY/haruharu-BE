package org.son.haruharu.domain.profile.implement;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UserProfileWriter {

    private final UserProfileRepository userProfileRepository;

    public UserProfileWriter(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public UserProfile save(UserProfile userProfile) {
        return userProfileRepository.save(userProfile);
    }

    /** 기수 신청 연락처만 바꾼다. 프로필이 없으면 false. */
    public boolean updateApplicantContact(Long userId, String school, String department, String phone,
                                          LocalDateTime now) {
        return userProfileRepository.updateApplicantContact(userId, school, department, phone, now) == 1;
    }

    public void deleteByUserId(Long userId) {
        userProfileRepository.deleteByUserId(userId);
    }
}
