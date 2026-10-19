package org.son.haruharu.domain.room.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.exception.InvalidRoomCompositionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomCompositionTest {

    @Test
    @DisplayName("기수·이름·성별 정원을 그대로 담고 이름 앞뒤 공백은 지운다")
    void givenValues_whenOf_thenKeepsValuesAndTrimsName() {
        RoomComposition composition = RoomComposition.of(4L, "  서울 6기 1조 ", 3, 2);

        assertThat(composition.cohortId()).isEqualTo(4L);
        assertThat(composition.name()).isEqualTo("서울 6기 1조");
        assertThat(composition.maleCapacity()).isEqualTo(3);
        assertThat(composition.femaleCapacity()).isEqualTo(2);
    }

    @Test
    @DisplayName("모두 비워도 된다(웹 전용 방). 빈 이름은 이름 없음으로 본다")
    void givenNothing_whenOf_thenEmpty() {
        assertThat(RoomComposition.of(null, null, null, null)).isEqualTo(RoomComposition.NONE);
        assertThat(RoomComposition.of(null, "   ", null, null).name()).isNull();
    }

    @Test
    @DisplayName("성별 정원이 1 미만이거나 이름이 50자를 넘으면 칸별로 모아 거부한다")
    void givenInvalidValues_whenOf_thenFieldErrors() {
        assertThatThrownBy(() -> RoomComposition.of(null, "가".repeat(51), 0, -1))
                .isInstanceOfSatisfying(InvalidRoomCompositionException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(error -> error.field())
                                .containsExactly("name", "male_capacity", "female_capacity"));
        assertThat(RoomComposition.of(null, "가".repeat(50), 1, 1).name()).hasSize(50);
    }

    @Test
    @DisplayName("DB 에서 복원하는 생성자는 잘못된 정원도 그대로 담는다 — 검증은 새 값을 만드는 of() 가 한다")
    void givenInvalidStoredValues_whenRestore_thenKeepsThemWithoutThrowing() {
        RoomComposition restored = new RoomComposition(null, null, 0, -1);

        assertThat(restored.maleCapacity()).isZero();
        assertThat(restored.femaleCapacity()).isEqualTo(-1);
    }
}
