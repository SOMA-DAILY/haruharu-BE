package org.son.haruharu.domain.profile.implement;

import org.son.haruharu.domain.profile.implement.exception.InvalidProfileFieldException;

import java.util.Optional;
import java.util.function.Function;

/**
 * 방 세션에서 열쇠로 해금하는 프로필 항목. field_key ↔ 라벨 ↔ UserProfile 값 추출을 정의한다.
 * 해금 비용·해금 가능 Day는 코드가 아닌 profile_field_catalog(데이터)에서 관리한다(항목 추가 시 여기에 상수 1줄 + 시드 1행).
 */
public enum ProfileField {

    AFFILIATION("affiliation", "직업", UserProfile::affiliation),
    HEIGHT("height", "키", p -> p.height() == null ? null : String.valueOf(p.height())),
    MBTI("mbti", "MBTI", UserProfile::mbti);

    private final String key;
    private final String label;
    private final Function<UserProfile, String> extractor;

    ProfileField(String key, String label, Function<UserProfile, String> extractor) {
        this.key = key;
        this.label = label;
        this.extractor = extractor;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String extract(UserProfile profile) {
        return profile == null ? null : extractor.apply(profile);
    }

    /** 해금 요청 경로용: 잘못된 field_key면 예외. */
    public static ProfileField fromKey(String key) {
        return findByKey(key).orElseThrow(InvalidProfileFieldException::new);
    }

    /** 조회 경로용: 카탈로그 행에 대응하는 enum이 없으면(제거된 항목) 조용히 skip 하기 위해 Optional 반환. */
    public static Optional<ProfileField> findByKey(String key) {
        for (ProfileField field : values()) {
            if (field.key.equals(key)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }
}
