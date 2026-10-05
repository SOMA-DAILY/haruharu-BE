package org.son.haruharu.domain.profile.implement;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.profile.implement.exception.InvalidRoomProfileException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 방 참가 전 온보딩 프로필 4칸(#455): 출생연도, MBTI, 학과, 요즘 빠져 있는 것.
 * 나이는 만 나이가 아니라 올해 − 출생연도다(앱 시트 표기 기준). 글자 수는 이모지를 한 글자로 센다(codePoint).
 */
public record RoomProfile(Integer birthYear, String mbti, String department, String intoNow) {

    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 35;
    public static final int DEPARTMENT_MAX_LENGTH = 15;
    public static final int INTO_NOW_MAX_LENGTH = 40;

    private static final Set<String> MBTI_TYPES = Set.of(
            "ISTJ", "ISFJ", "INFJ", "INTJ", "ISTP", "ISFP", "INFP", "INTP",
            "ESTP", "ESFP", "ENFP", "ENTP", "ESTJ", "ESFJ", "ENFJ", "ENTJ");

    public static RoomProfile of(UserProfile profile) {
        return new RoomProfile(profile.birthYear(), profile.mbti(), profile.department(), profile.intoNow());
    }

    /** 4칸이 다 있고 MBTI 가 16개 중 하나일 때 완료. 서버는 완료 여부로 피드·채팅을 막지 않는다(앱만 막음). */
    public boolean completed() {
        return birthYear != null && isMbti(mbti) && !isBlank(department) && !isBlank(intoNow);
    }

    /** 올해 − 출생연도. 출생연도가 없으면 null. */
    public Integer ageIn(int currentYear) {
        return birthYear == null ? null : currentYear - birthYear;
    }

    /**
     * 요청값을 정규화(mbti 대문자, 학과·요즘 빠져 있는 것 trim)하고 검증한다. 틀린 칸은 한 번에 모아 400 으로 던진다.
     * 출생연도 범위는 올해−35 ~ 올해−18.
     */
    public static RoomProfile validated(Integer birthYear, String mbti, String department, String intoNow,
                                        int currentYear) {
        String normalizedMbti = mbti == null ? null : mbti.trim().toUpperCase(Locale.ROOT);
        String trimmedDepartment = department == null ? null : department.trim();
        String trimmedIntoNow = intoNow == null ? null : intoNow.trim();

        List<ApiFieldError> errors = new ArrayList<>();
        if (birthYear == null) {
            errors.add(new ApiFieldError("birth_year", "출생연도를 골라주세요"));
        } else if (birthYear < currentYear - MAX_AGE || birthYear > currentYear - MIN_AGE) {
            errors.add(new ApiFieldError("birth_year", "출생연도를 다시 골라주세요"));
        }
        if (isBlank(normalizedMbti)) {
            errors.add(new ApiFieldError("mbti", "MBTI를 골라주세요"));
        } else if (!isMbti(normalizedMbti)) {
            errors.add(new ApiFieldError("mbti", "MBTI를 다시 골라주세요"));
        }
        if (isBlank(trimmedDepartment)) {
            errors.add(new ApiFieldError("department", "학과를 써주세요"));
        } else if (length(trimmedDepartment) > DEPARTMENT_MAX_LENGTH) {
            errors.add(new ApiFieldError("department", "학과는 15자까지 쓸 수 있어요"));
        }
        if (isBlank(trimmedIntoNow)) {
            errors.add(new ApiFieldError("into_now", "요즘 빠져 있는 것을 써주세요"));
        } else if (length(trimmedIntoNow) > INTO_NOW_MAX_LENGTH) {
            errors.add(new ApiFieldError("into_now", "요즘 빠져 있는 것은 40자까지 쓸 수 있어요"));
        }
        if (!errors.isEmpty()) {
            throw new InvalidRoomProfileException(errors);
        }
        return new RoomProfile(birthYear, normalizedMbti, trimmedDepartment, trimmedIntoNow);
    }

    public static boolean isMbti(String value) {
        return value != null && MBTI_TYPES.contains(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
