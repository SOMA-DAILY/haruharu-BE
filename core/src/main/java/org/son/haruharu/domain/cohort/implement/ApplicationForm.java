package org.son.haruharu.domain.cohort.implement;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.cohort.implement.exception.InvalidCohortApplicationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 신청서. 모든 트랙에 공통인 학교·학과·번호와 트랙별 추가 문항의 답(key → 답).
 * 학교·학과·번호는 프로필에도 저장한다(#511). 학과 길이는 프로필 학과 칸(15자)과 맞춘다.
 */
public record ApplicationForm(String school, String department, String phone, Map<String, String> answers) {

    static final int SCHOOL_MAX = 40;
    static final int DEPARTMENT_MAX = 15;
    static final int ANSWER_MAX = 200;
    /** 휴대전화 번호(숫자만). 하이픈·공백은 저장 전에 지운다. */
    private static final Pattern MOBILE = Pattern.compile("^01[016789]\\d{7,8}$");

    public ApplicationForm {
        answers = answers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(answers));
    }

    /**
     * 앞뒤 공백을 지우고 번호를 숫자만 남긴 신청서를 돌려준다. 잘못된 칸은 모아서 한 번에 알려준다.
     * 답은 트랙 문항 기준으로 본다 — 모르는 키는 거절하고, 비어 있는 선택 문항은 저장하지 않는다.
     */
    public ApplicationForm validated(List<TrackQuestion> questions) {
        List<ApiFieldError> errors = new ArrayList<>();
        String trimmedSchool = trim(school);
        if (trimmedSchool == null || trimmedSchool.length() > SCHOOL_MAX) {
            errors.add(new ApiFieldError("school", "학교는 1~" + SCHOOL_MAX + "자로 써 주세요."));
        }
        String trimmedDepartment = trim(department);
        if (trimmedDepartment == null || trimmedDepartment.length() > DEPARTMENT_MAX) {
            errors.add(new ApiFieldError("department", "학과는 1~" + DEPARTMENT_MAX + "자로 써 주세요."));
        }
        String digits = phone == null ? null : phone.replaceAll("[\\s-]", "");
        if (digits == null || !MOBILE.matcher(digits).matches()) {
            errors.add(new ApiFieldError("phone", "휴대전화 번호를 확인해 주세요."));
        }
        Map<String, String> validAnswers = validateAnswers(questions, errors);
        if (!errors.isEmpty()) throw new InvalidCohortApplicationException(errors);
        return new ApplicationForm(trimmedSchool, trimmedDepartment, digits, validAnswers);
    }

    private Map<String, String> validateAnswers(List<TrackQuestion> questions, List<ApiFieldError> errors) {
        Set<String> known = questions.stream().map(TrackQuestion::key).collect(Collectors.toSet());
        // 모르는 키는 한 줄로만 알린다. 받은 키를 칸 이름에 싣지 않는다 — 응답·로그에 남의 입력이 그대로 찍히지 않게.
        if (!known.containsAll(answers.keySet())) {
            errors.add(new ApiFieldError("answers", "알 수 없는 문항이 있습니다."));
        }
        Map<String, String> valid = new LinkedHashMap<>();
        for (TrackQuestion question : questions) {
            String field = "answers." + question.key();
            String answer = trim(answers.get(question.key()));
            if (answer == null) {
                if (question.required()) errors.add(new ApiFieldError(field, "답을 입력해 주세요."));
                continue;
            }
            if (question.type() == TrackQuestionType.CHOICE && !question.options().contains(answer)) {
                errors.add(new ApiFieldError(field, "보기 중에서 골라 주세요."));
            } else if (answer.length() > ANSWER_MAX) {
                errors.add(new ApiFieldError(field, "답은 " + ANSWER_MAX + "자까지 쓸 수 있어요."));
            } else {
                valid.put(question.key(), answer);
            }
        }
        return valid;
    }

    private static String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
