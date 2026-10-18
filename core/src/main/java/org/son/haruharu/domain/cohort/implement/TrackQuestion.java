package org.son.haruharu.domain.cohort.implement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 트랙별 추가 문항. 학교·학과·번호처럼 모든 트랙에 공통인 항목은 신청 칸으로 따로 두고,
 * 트랙마다 다른 질문만 여기 둔다. 신청 시 답은 key 로 묶어 저장한다(#511).
 *
 * @param key      답을 묶는 키(트랙 안에서 유일, 소문자·숫자·밑줄)
 * @param label    앱에 보이는 질문
 * @param type     TEXT(자유 입력)·CHOICE(보기 중 하나)
 * @param required 필수 여부
 * @param options  CHOICE 의 보기. TEXT 는 빈 목록
 */
public record TrackQuestion(
        String key,
        String label,
        TrackQuestionType type,
        boolean required,
        List<String> options
) {
    public TrackQuestion {
        // null 보기는 남겨 두고 RecruitTrack 검증이 칸 오류로 거절한다(List.copyOf 는 NPE).
        options = options == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(options));
    }

    /** jsonb 한 칸에 담는 형태. 필드 이름은 앱 응답과 같은 snake_case 로 둔다. */
    public Map<String, Object> toStored() {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("key", key);
        stored.put("label", label);
        stored.put("type", type.name());
        stored.put("required", required);
        stored.put("options", new ArrayList<>(options));
        return stored;
    }

    public static TrackQuestion fromStored(Map<String, Object> stored) {
        Object options = stored.get("options");
        return new TrackQuestion(
                (String) stored.get("key"),
                (String) stored.get("label"),
                TrackQuestionType.valueOf((String) stored.get("type")),
                Boolean.TRUE.equals(stored.get("required")),
                options instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of());
    }
}
