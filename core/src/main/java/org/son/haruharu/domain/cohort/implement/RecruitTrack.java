package org.son.haruharu.domain.cohort.implement;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.cohort.implement.exception.InvalidRecruitTrackException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 모집 트랙. "서울 대학생"처럼 오래 유지되는 모집 라인이다. 회차(기수)는 {@link Cohort} 로 따로 둔다.
 * key 는 운영·앱이 트랙을 가리키는 고정 이름이라 만든 뒤 바꾸지 않는다.
 */
public record RecruitTrack(
        Long id,
        String key,
        String label,
        String region,
        RecruitAudience audience,
        int minAge,
        int maxAge,
        List<TrackQuestion> questions,
        int displayOrder,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    private static final Pattern KEY = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern QUESTION_KEY = Pattern.compile("^[a-z][a-z0-9_]*$");
    static final int KEY_MAX = 40;
    static final int LABEL_MAX = 40;
    static final int REGION_MAX = 40;
    static final int QUESTION_LABEL_MAX = 100;
    static final int QUESTIONS_MAX = 20;
    static final int OPTIONS_MAX = 20;
    /** 성인만 받는다. 실제 나이 판정은 PASS 생년월일로 한다(#512). */
    static final int ADULT_AGE = 19;
    static final int AGE_MAX = 100;

    public RecruitTrack {
        // List.copyOf 는 null 원소에서 NPE 를 던진다. null 을 남겨 두고 검증이 칸 오류(400)로 거절하게 한다.
        questions = questions == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(questions));
    }

    /** 새 트랙(활성). 잘못된 칸은 모아서 한 번에 알려준다. */
    public static RecruitTrack create(String key, String label, String region, RecruitAudience audience,
                                      Integer minAge, Integer maxAge, List<TrackQuestion> questions,
                                      int displayOrder) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (key == null || key.length() > KEY_MAX || !KEY.matcher(key).matches()) {
            errors.add(new ApiFieldError("key", "키는 소문자·숫자·하이픈(-)으로 " + KEY_MAX + "자 이하여야 합니다."));
        }
        RecruitTrack track = new RecruitTrack(null, key, label, region, audience,
                minAge == null ? 0 : minAge, maxAge == null ? 0 : maxAge, questions, displayOrder, true, null, null);
        track.collectErrors(errors, minAge, maxAge);
        if (!errors.isEmpty()) throw new InvalidRecruitTrackException(errors);
        return track;
    }

    /** 보낸 칸(null 이 아닌 값)만 바꾼다. key 는 바꾸지 않는다. */
    public RecruitTrack revise(String label, String region, RecruitAudience audience, Integer minAge, Integer maxAge,
                               List<TrackQuestion> questions, Integer displayOrder, Boolean active) {
        RecruitTrack revised = new RecruitTrack(id, key,
                label != null ? label : this.label,
                region != null ? region : this.region,
                audience != null ? audience : this.audience,
                minAge != null ? minAge : this.minAge,
                maxAge != null ? maxAge : this.maxAge,
                questions != null ? questions : this.questions,
                displayOrder != null ? displayOrder : this.displayOrder,
                active != null ? active : this.active,
                createdAt, updatedAt);
        List<ApiFieldError> errors = new ArrayList<>();
        revised.collectErrors(errors, revised.minAge, revised.maxAge);
        if (!errors.isEmpty()) throw new InvalidRecruitTrackException(errors);
        return revised;
    }

    /** 나이(신청 판정은 연 나이)가 트랙 범위(양 끝 포함) 안인지. */
    public boolean acceptsAge(int age) {
        return age >= minAge && age <= maxAge;
    }

    private void collectErrors(List<ApiFieldError> errors, Integer minAge, Integer maxAge) {
        if (isBlank(label) || label.length() > LABEL_MAX) {
            errors.add(new ApiFieldError("label", "트랙 이름은 1~" + LABEL_MAX + "자여야 합니다."));
        }
        if (isBlank(region) || region.length() > REGION_MAX) {
            errors.add(new ApiFieldError("region", "지역은 1~" + REGION_MAX + "자여야 합니다."));
        }
        if (audience == null) {
            errors.add(new ApiFieldError("audience", "모집 대상을 골라 주세요."));
        }
        if (minAge == null || minAge < ADULT_AGE || minAge > AGE_MAX) {
            errors.add(new ApiFieldError("min_age", "최소 나이는 " + ADULT_AGE + "~" + AGE_MAX + "세여야 합니다."));
        } else if (maxAge == null || maxAge < minAge || maxAge > AGE_MAX) {
            errors.add(new ApiFieldError("max_age", "최대 나이는 최소 나이 이상 " + AGE_MAX + "세 이하여야 합니다."));
        }
        if (displayOrder < 0) {
            errors.add(new ApiFieldError("display_order", "표시 순서는 0 이상이어야 합니다."));
        }
        collectQuestionErrors(errors);
    }

    private void collectQuestionErrors(List<ApiFieldError> errors) {
        if (questions.size() > QUESTIONS_MAX) {
            errors.add(new ApiFieldError("questions", "추가 문항은 " + QUESTIONS_MAX + "개까지입니다."));
            return;
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < questions.size(); i++) {
            String prefix = "questions[" + i + "].";
            TrackQuestion question = questions.get(i);
            if (question == null) {
                errors.add(new ApiFieldError("questions[" + i + "]", "문항이 비어 있습니다."));
                continue;
            }
            if (question.key() == null || question.key().length() > KEY_MAX
                    || !QUESTION_KEY.matcher(question.key()).matches()) {
                errors.add(new ApiFieldError(prefix + "key", "문항 키는 소문자로 시작하는 소문자·숫자·밑줄(_) "
                        + KEY_MAX + "자 이하여야 합니다."));
            } else if (!keys.add(question.key())) {
                errors.add(new ApiFieldError(prefix + "key", "문항 키가 겹칩니다."));
            }
            if (isBlank(question.label()) || question.label().length() > QUESTION_LABEL_MAX) {
                errors.add(new ApiFieldError(prefix + "label", "질문은 1~" + QUESTION_LABEL_MAX + "자여야 합니다."));
            }
            if (question.type() == null) {
                errors.add(new ApiFieldError(prefix + "type", "문항 형식을 골라 주세요."));
            } else if (question.type() == TrackQuestionType.CHOICE && !validOptions(question.options())) {
                errors.add(new ApiFieldError(prefix + "options", "선택형 문항은 서로 다른 보기 2~" + OPTIONS_MAX
                        + "개가 필요합니다."));
            } else if (question.type() == TrackQuestionType.TEXT && !question.options().isEmpty()) {
                errors.add(new ApiFieldError(prefix + "options", "입력형 문항에는 보기를 둘 수 없습니다."));
            }
        }
    }

    private static boolean validOptions(List<String> options) {
        if (options.size() < 2 || options.size() > OPTIONS_MAX) return false;
        Set<String> seen = new HashSet<>();
        for (String option : options) {
            if (isBlank(option) || option.length() > QUESTION_LABEL_MAX || !seen.add(option)) return false;
        }
        return true;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
