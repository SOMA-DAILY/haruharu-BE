package org.son.haruharu.domain.profile.implement;

public record UserProfile(
        Long id,
        Long userId,
        String location,
        String affiliation,
        Integer height,
        String mbti,
        String nickname,
        DrinkingStatus drinkingStatus,
        SmokingStatus smokingStatus,
        Gender gender,
        // 방 참가 전 온보딩 프로필(#455). 나이는 올해 − birthYear. 학과는 affiliation(하는 일)과 섞지 않는다.
        Integer birthYear,
        String department,
        String intoNow
) {
    /** 온보딩 프로필(#455) 이전 10-arg 호환. 새 칸은 null. */
    public UserProfile(Long id, Long userId, String location, String affiliation, Integer height, String mbti,
                       String nickname, DrinkingStatus drinkingStatus, SmokingStatus smokingStatus, Gender gender) {
        this(id, userId, location, affiliation, height, mbti, nickname, drinkingStatus, smokingStatus, gender,
                null, null, null);
    }

    /**
     * 아직 영속화되지 않은 빈 프로필. id는 null로 두어 저장 시 @GeneratedValue가 생성하게 한다.
     * 생년월일은 본인인증(identity)이 단일 출처이므로 프로필에 두지 않는다. 성별은 온보딩에서 자기신고로 수집한다(#392).
     */
    public static UserProfile empty(Long userId) {
        return new UserProfile(null, userId, null, null, null, null, null, null, null, null);
    }

    /** 방 온보딩 4칸만 바꾼 새 인스턴스(#455). mbti 는 기존 칸을 같이 쓴다. */
    public UserProfile withRoomProfile(Integer birthYear, String mbti, String department, String intoNow) {
        return new UserProfile(id, userId, location, affiliation, height, mbti, nickname, drinkingStatus,
                smokingStatus, gender, birthYear, department, intoNow);
    }
}
