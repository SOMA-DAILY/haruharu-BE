package org.son.haruharu.domain.profile.implement;

import java.util.function.Function;

/**
 * 방 세션에서 currentDay에 따라 자동 공개하는 프로필 항목. field_key ↔ 라벨 ↔ 공개Day ↔ UserProfile 값 추출을 정의한다.
 * currentDay >= availableFromDay 이면 전원에게 노출된다(재화·해금 없음). 항목 추가 시 상수 1줄만 추가한다.
 */
public enum ProfileField {

    LOCATION("location", "지역", 3, UserProfile::location),
    AFFILIATION("affiliation", "직업", 3, UserProfile::affiliation),
    HEIGHT("height", "키", 3, p -> p.height() == null ? null : String.valueOf(p.height())),
    MBTI("mbti", "MBTI", 3, UserProfile::mbti),
    DRINKING("drinking", "음주", 3, UserProfile::drinkingStatus),
    SMOKING("smoking", "흡연", 3, UserProfile::smokingStatus);

    private final String key;
    private final String label;
    private final int availableFromDay;
    private final Function<UserProfile, String> extractor;

    ProfileField(String key, String label, int availableFromDay, Function<UserProfile, String> extractor) {
        this.key = key;
        this.label = label;
        this.availableFromDay = availableFromDay;
        this.extractor = extractor;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public int availableFromDay() {
        return availableFromDay;
    }

    public String extract(UserProfile profile) {
        return profile == null ? null : extractor.apply(profile);
    }
}
