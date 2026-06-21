package org.son.haruharu.domain.profile.implement;

import java.time.LocalDate;
import java.util.UUID;

public record UserProfile(
        UUID id,
        UUID userId,
        String gender,
        LocalDate birthday,
        String location,
        String affiliation,
        Integer height,
        String mbti,
        String nickname,
        String drinkingStatus,
        String smokingStatus,
        String religion,
        String atmosphereKeywords
) {}
