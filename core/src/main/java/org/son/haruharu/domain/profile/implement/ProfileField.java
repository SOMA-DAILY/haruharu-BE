package org.son.haruharu.domain.profile.implement;

/**
 * 방 멤버 프로필 시트에 보이는 항목(#457, F2). 선언 순서가 시트 표시 순서다(MBTI, 요즘 빠져 있는 것, 나이, 학과).
 * 공개 시각은 방 편성(room_schedule_items.reveal_fields)이 정하고, 편성에 공개 행이 없는 옛 방은 DAY3(availableFromDay) 규칙을 쓴다.
 * key 는 enum 이름(mission MissionRevealField 와 같은 이름). AGE 값은 계정 프로필 밖(birth_year / RoomMember.birthday)에서 계산한다.
 */
public enum ProfileField {

    MBTI("MBTI"),
    INTO_NOW("요즘 빠져 있는 것"),
    AGE("나이"),
    DEPARTMENT("학과");

    /** 편성에 공개 행이 없는 옛 방의 공개 Day. */
    public static final int LEGACY_REVEAL_DAY = 3;

    private final String label;

    ProfileField(String label) {
        this.label = label;
    }

    public String key() {
        return name();
    }

    public String label() {
        return label;
    }

    public int availableFromDay() {
        return LEGACY_REVEAL_DAY;
    }

    /** 계정 프로필에서 바로 읽는 값. AGE 는 null(호출자가 계산). */
    public String extract(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        return switch (this) {
            case MBTI -> profile.mbti();
            case INTO_NOW -> profile.intoNow();
            case DEPARTMENT -> profile.department();
            case AGE -> null;
        };
    }
}
