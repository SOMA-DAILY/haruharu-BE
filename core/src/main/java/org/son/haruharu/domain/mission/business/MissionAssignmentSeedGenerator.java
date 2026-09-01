package org.son.haruharu.domain.mission.business;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class MissionAssignmentSeedGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public long nextSeed() {
        return secureRandom.nextLong();
    }
}
