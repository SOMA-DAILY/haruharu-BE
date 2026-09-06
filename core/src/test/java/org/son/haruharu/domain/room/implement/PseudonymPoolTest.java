package org.son.haruharu.domain.room.implement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PseudonymPoolTest {

    @Test
    @DisplayName("남성은 입장 순번대로 남성 풀 가명이 배정된다")
    void givenMaleGender_whenAssignByJoinIndex_thenReturnsMalePoolInOrder() {
        assertThat(PseudonymPool.assign("MALE", 0)).isEqualTo("민준");
        assertThat(PseudonymPool.assign("MALE", 1)).isEqualTo("정석");
        assertThat(PseudonymPool.assign("MALE", 2)).isEqualTo("현우");
        assertThat(PseudonymPool.assign("MALE", 5)).isEqualTo("하준");
    }

    @Test
    @DisplayName("여성은 입장 순번대로 여성 풀 가명이 배정된다")
    void givenFemaleGender_whenAssignByJoinIndex_thenReturnsFemalePoolInOrder() {
        assertThat(PseudonymPool.assign("FEMALE", 0)).isEqualTo("유진");
        assertThat(PseudonymPool.assign("FEMALE", 1)).isEqualTo("지우");
        assertThat(PseudonymPool.assign("FEMALE", 2)).isEqualTo("서연");
        assertThat(PseudonymPool.assign("FEMALE", 5)).isEqualTo("지아");
    }

    @Test
    @DisplayName("풀 크기(6)를 넘어서면 순환하며 숫자 접미사가 붙는다")
    void givenIndexBeyondPoolSize_whenAssign_thenCyclesWithSuffix() {
        assertThat(PseudonymPool.assign("MALE", 6)).isEqualTo("민준2");
        assertThat(PseudonymPool.assign("MALE", 7)).isEqualTo("정석2");
        assertThat(PseudonymPool.assign("MALE", 12)).isEqualTo("민준3");
        assertThat(PseudonymPool.assign("FEMALE", 6)).isEqualTo("유진2");
    }

    @Test
    @DisplayName("성별이 null이거나 미지원 값이면 가명을 배정할 수 없어 null을 반환한다")
    void givenUnknownGender_whenAssign_thenReturnsNull() {
        assertThat(PseudonymPool.assign(null, 0)).isNull();
        assertThat(PseudonymPool.assign("OTHER", 0)).isNull();
        assertThat(PseudonymPool.assign("male", 0)).isNull();
    }
}
