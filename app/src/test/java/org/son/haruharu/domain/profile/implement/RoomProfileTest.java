package org.son.haruharu.domain.profile.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.profile.implement.exception.InvalidRoomProfileException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class RoomProfileTest {

    private static final int YEAR = 2026;

    @Test
    @DisplayName("mbti 는 대문자로, 학과와 요즘 빠져 있는 것은 trim 해서 저장값을 만든다")
    void givenLowercaseMbtiAndPadding_whenValidated_thenNormalizes() {
        RoomProfile profile = RoomProfile.validated(2003, " enfp ", "  전자공학과 ", " 러닝 ", YEAR);

        assertThat(profile).isEqualTo(new RoomProfile(2003, "ENFP", "전자공학과", "러닝"));
        assertThat(profile.completed()).isTrue();
        assertThat(profile.ageIn(YEAR)).isEqualTo(23);
    }

    @Test
    @DisplayName("16개 밖 MBTI, 범위 밖 출생연도, 길이 초과는 칸별 메시지를 한 번에 모아 400 으로 던진다")
    void givenInvalidValues_whenValidated_thenCollectsFieldErrors() {
        assertThatThrownBy(() -> RoomProfile.validated(YEAR - 17, "ABCD", "가".repeat(16), "나".repeat(41), YEAR))
                .isInstanceOf(InvalidRoomProfileException.class)
                .satisfies(e -> assertThat(((InvalidRoomProfileException) e).getFieldErrors())
                        .extracting(ApiFieldError::field, ApiFieldError::message)
                        .containsExactly(
                                tuple("birth_year", "출생연도를 다시 골라주세요"),
                                tuple("mbti", "MBTI를 다시 골라주세요"),
                                tuple("department", "학과는 15자까지 쓸 수 있어요"),
                                tuple("into_now", "요즘 빠져 있는 것은 40자까지 쓸 수 있어요")))
                .hasMessage("출생연도를 다시 골라주세요");
    }

    @Test
    @DisplayName("출생연도는 올해−35 ~ 올해−18 경계를 포함한다")
    void givenBoundaryYears_whenValidated_thenAccepts() {
        assertThat(RoomProfile.validated(YEAR - 35, "INTJ", "a", "b", YEAR).birthYear()).isEqualTo(YEAR - 35);
        assertThat(RoomProfile.validated(YEAR - 18, "INTJ", "a", "b", YEAR).birthYear()).isEqualTo(YEAR - 18);
        assertThatThrownBy(() -> RoomProfile.validated(YEAR - 36, "INTJ", "a", "b", YEAR))
                .isInstanceOf(InvalidRoomProfileException.class);
    }

    @Test
    @DisplayName("빈 칸은 필수 메시지, 학과 15자와 이모지는 한 글자로 센다")
    void givenBlankAndEmoji_whenValidated_thenRequiredMessagesAndCodePointLength() {
        assertThatThrownBy(() -> RoomProfile.validated(null, " ", "", null, YEAR))
                .isInstanceOf(InvalidRoomProfileException.class)
                .satisfies(e -> assertThat(((InvalidRoomProfileException) e).getFieldErrors())
                        .extracting(ApiFieldError::message)
                        .containsExactly("출생연도를 골라주세요", "MBTI를 골라주세요", "학과를 써주세요",
                                "요즘 빠져 있는 것을 써주세요"));
        assertThat(RoomProfile.validated(2003, "INFP", "🏃".repeat(15), "🔥".repeat(40), YEAR).completed()).isTrue();
    }

    @Test
    @DisplayName("4칸 중 하나라도 없거나 MBTI 가 16개 밖이면 completed=false")
    void givenMissingOrInvalid_whenCompleted_thenFalse() {
        assertThat(new RoomProfile(2003, "ENFP", "전자공학과", null).completed()).isFalse();
        assertThat(new RoomProfile(2003, "XXXX", "전자공학과", "러닝").completed()).isFalse();
        assertThat(new RoomProfile(null, "ENFP", "전자공학과", "러닝").completed()).isFalse();
    }
}
