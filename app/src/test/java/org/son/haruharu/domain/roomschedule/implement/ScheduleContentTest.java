package org.son.haruharu.domain.roomschedule.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.roomschedule.business.dto.RoomScheduleItemResponse;
import org.son.haruharu.domain.roomschedule.implement.exception.InvalidScheduleItemException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleContentTest {

    @Test
    @DisplayName("공개 항목은 중복 없이 표시 순서로 정리하고, 빈 문자열은 null 로 본다")
    void givenMessyInput_whenValidated_thenNormalizes() {
        ScheduleContent content = ScheduleContent.validated(List.of("into_now", "MBTI", "MBTI"), "  ", null, "", " ", "");

        assertThat(content.revealFields()).containsExactly(RevealField.MBTI, RevealField.INTO_NOW);
        assertThat(content.messageContent()).isNull();
        assertThat(content.push()).isFalse();
        assertThat(content.pushTitle()).isNull();
        assertThat(RevealField.join(content.revealFields())).isEqualTo("MBTI,INTO_NOW");
    }

    @Test
    @DisplayName("검증은 둘뿐: 글이나 공개 항목 하나는 필수, 글자 수 500/15/30(이모지 한 글자)")
    void givenInvalid_whenValidated_thenFieldErrors() {
        assertThatThrownBy(() -> ScheduleContent.validated(List.of(), null, true, null, null, ""))
                .isInstanceOf(InvalidScheduleItemException.class)
                .satisfies(e -> assertThat(((InvalidScheduleItemException) e).getFieldErrors())
                        .containsExactly(new ApiFieldError("message_content", "운영 글이나 공개 항목 중 하나는 있어야 해요")));
        assertThatThrownBy(() -> ScheduleContent.validated(List.of("XX"), "가".repeat(501), true, "가".repeat(16),
                "가".repeat(31), "chat_schedule[1]."))
                .isInstanceOf(InvalidScheduleItemException.class)
                .satisfies(e -> assertThat(((InvalidScheduleItemException) e).getFieldErrors())
                        .extracting(ApiFieldError::field)
                        .containsExactly("chat_schedule[1].reveal_fields", "chat_schedule[1].message_content",
                                "chat_schedule[1].push_title", "chat_schedule[1].push_body"));
        assertThat(ScheduleContent.validated(null, "공지", true, "📢" + "가".repeat(14), "🔥".repeat(30), "").push())
                .isTrue();
    }

    @Test
    @DisplayName("PROFILE_REVEAL 문구: 1개면 항목 고정 문구, 2개 이상이면 합친 문구")
    void givenFields_whenPushText_thenSingleOrCombined() {
        assertThat(RevealField.pushTitleOf(List.of(RevealField.MBTI))).isEqualTo("다들 MBTI 뭘까요?");
        assertThat(RevealField.pushBodyOf(List.of(RevealField.DEPARTMENT))).isEqualTo("방 사람들 학과 확인해봐요");
        assertThat(RevealField.pushTitleOf(List.of(RevealField.MBTI, RevealField.INTO_NOW))).isEqualTo("새 정보가 공개됐어요");
        assertThat(RevealField.pushBodyOf(List.of(RevealField.MBTI, RevealField.INTO_NOW)))
                .isEqualTo("MBTI, 요즘 빠져 있는 것 를 지금 확인해봐요");
    }

    @Test
    @DisplayName("DAY 는 달력 날짜 차 + 1 — 09:00 시작 방의 둘째 날 08:00 은 DAY2, 시작 전날은 0")
    void givenCalendarDates_whenDayOf_thenCalendarRule() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 11, 9, 0);
        Room room = Room.createScheduled(start, start.plusHours(56), start.plusHours(59), start.plusHours(60),
                start.plusHours(72), 3);

        assertThat(RoomScheduleItemResponse.dayOf(LocalDateTime.of(2026, 10, 12, 8, 0), room)).isEqualTo(2);
        assertThat(RoomScheduleItemResponse.dayOf(LocalDateTime.of(2026, 10, 11, 8, 0), room)).isEqualTo(1);
        assertThat(RoomScheduleItemResponse.dayOf(LocalDateTime.of(2026, 10, 10, 20, 0), room)).isEqualTo(0);
    }
}
