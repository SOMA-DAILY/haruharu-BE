package org.son.haruharu.domain.profile.implement;

import org.springframework.stereotype.Component;

@Component
public class ProfileFieldUnlockWriter {

    private final ProfileFieldUnlockRepository profileFieldUnlockRepository;

    public ProfileFieldUnlockWriter(ProfileFieldUnlockRepository profileFieldUnlockRepository) {
        this.profileFieldUnlockRepository = profileFieldUnlockRepository;
    }

    public ProfileFieldUnlock save(ProfileFieldUnlock unlock) {
        return profileFieldUnlockRepository.save(unlock);
    }
}
