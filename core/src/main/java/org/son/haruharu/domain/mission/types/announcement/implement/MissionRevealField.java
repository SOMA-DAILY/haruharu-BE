package org.son.haruharu.domain.mission.types.announcement.implement;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.son.haruharu.domain.profile.implement.ProfileField;

/**
 * 편성으로 공개하는 프로필 항목(#457, F2). 시트 표시 순서와 같다. PROFILE_REVEAL 푸시 문구(서버 고정값)를 함께 가진다.
 * DB 에는 쉼표 목록(`MBTI,INTO_NOW`)으로 저장한다.
 */
public enum MissionRevealField {
    MBTI("MBTI", "다들 MBTI 뭘까요?", "영상 보면서 생각한 거랑 맞는지 봐요"),
    INTO_NOW("요즘 빠져 있는 것", "요즘 다들 뭐에 빠져 있을까요?", "나랑 겹치는 사람 있는지 확인해봐요"),
    AGE("나이", "영상 보고 몇 살 같았어요?", "예상이 맞았는지 지금 확인해봐요"),
    DEPARTMENT("학과", "문과일까, 이과일까?", "방 사람들 학과 확인해봐요");

    /** 항목 2개 이상을 한 행에서 열 때의 알림 제목. */
    public static final String MULTI_TITLE = "새 정보가 공개됐어요";

    private final String label;
    private final String pushTitle;
    private final String pushBody;

    MissionRevealField(String label, String pushTitle, String pushBody) {
        this.label = label;
        this.pushTitle = pushTitle;
        this.pushBody = pushBody;
    }

    public String label() {
        return label;
    }

    public static Optional<MissionRevealField> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(f -> f.name().equalsIgnoreCase(name.trim())).findFirst();
    }

    /** 쉼표 목록 → 표시 순서로 정렬한 중복 없는 목록. 모르는 값은 버린다(저장은 검증을 거친 값만 들어간다). */
    public static List<MissionRevealField> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        Set<MissionRevealField> set = EnumSet.noneOf(MissionRevealField.class);
        for (String part : csv.split(",")) {
            parse(part).ifPresent(set::add);
        }
        return List.copyOf(set);
    }

    /** 표시 순서 쉼표 목록. 비어 있으면 null(컬럼 null). */
    public static String join(Collection<MissionRevealField> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        return EnumSet.copyOf(fields).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /** 이 항목이 여는 프로필 시트 칸. 항목을 추가하면 여기서 컴파일이 막힌다. */
    public ProfileField profileField() {
        return switch (this) {
            case MBTI -> ProfileField.MBTI;
            case INTO_NOW -> ProfileField.INTO_NOW;
            case AGE -> ProfileField.AGE;
            case DEPARTMENT -> ProfileField.DEPARTMENT;
        };
    }

    /** PROFILE_REVEAL 제목. 1개면 항목 고정 문구, 2개 이상이면 합친 문구. */
    public static String pushTitleOf(List<MissionRevealField> fields) {
        return fields.size() == 1 ? fields.get(0).pushTitle : MULTI_TITLE;
    }

    /** PROFILE_REVEAL 본문. 2개 이상이면 `{라벨1}, {라벨2} 를 지금 확인해봐요`. */
    public static String pushBodyOf(List<MissionRevealField> fields) {
        if (fields.size() == 1) {
            return fields.get(0).pushBody;
        }
        return fields.stream().map(MissionRevealField::label).collect(Collectors.joining(", ")) + " 를 지금 확인해봐요";
    }
}
