package org.son.haruharu.domain.mission.types.announcement.implement;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementContent;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.room.implement.Room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnouncementMissionDefinitionTest {

    @Test
    @DisplayName("공개 항목은 중복 없이 표시 순서로 정리하고, 빈 문자열은 null 로 본다")
    void givenMessyInput_whenValidated_thenNormalizes() {
        var fields = AnnouncementMissionDefinition.from(null, List.of("into_now", "MBTI", "MBTI")).revealFields();
        assertThat(fields).containsExactly(MissionRevealField.MBTI, MissionRevealField.INTO_NOW);
        assertThat(MissionAnnouncementContent.text("  ")).isNull();
        assertThat(MissionRevealField.join(fields)).isEqualTo("MBTI,INTO_NOW");
    }

    @Test
    @DisplayName("본문·공개 항목·푸시 길이의 기존 검증과 이모지 글자 수를 보존한다")
    void givenInvalid_whenValidated_thenFieldErrors() {

        var now = LocalDateTime.now();
        var blank = AnnouncementMissionDefinition.from(null, List.of());
        assertThatThrownBy(() -> blank.validate()).isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> AnnouncementMissionDefinition.from(null, List.of("XX"))).isInstanceOf(InvalidMissionException.class);
        var mission = RoomMission.create(1L, MissionType.ANNOUNCEMENT, now, null, "admin:1");
        assertThatThrownBy(() -> MissionAnnouncementContent.validateAnnouncement(mission, MissionAnnouncementType.START, now,
                "가".repeat(501), null, null, false)).isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> MissionAnnouncementContent.validateAnnouncement(mission, MissionAnnouncementType.START, now,
                "공지", "가".repeat(16), null, false)).isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> MissionAnnouncementContent.validateAnnouncement(mission, MissionAnnouncementType.START, now,
                "공지", null, "가".repeat(31), false)).isInstanceOf(InvalidMissionException.class);
        MissionAnnouncementContent.validateAnnouncement(mission, MissionAnnouncementType.START, now,
                "공지", "📢" + "가".repeat(14), "🔥".repeat(30), false);
    }

    @Test
    @DisplayName("PROFILE_REVEAL 문구: 1개면 항목 고정 문구, 2개 이상이면 합친 문구")
    void givenFields_whenPushText_thenSingleOrCombined() {
        assertThat(MissionRevealField.pushTitleOf(List.of(MissionRevealField.MBTI))).isEqualTo("다들 MBTI 뭘까요?");
        assertThat(MissionRevealField.pushBodyOf(List.of(MissionRevealField.DEPARTMENT))).isEqualTo("방 사람들 학과 확인해봐요");
        assertThat(MissionRevealField.pushTitleOf(List.of(MissionRevealField.MBTI, MissionRevealField.INTO_NOW))).isEqualTo("새 정보가 공개됐어요");
        assertThat(MissionRevealField.pushBodyOf(List.of(MissionRevealField.MBTI, MissionRevealField.INTO_NOW)))
                .isEqualTo("MBTI, 요즘 빠져 있는 것 를 지금 확인해봐요");
    }

    @Test
    @DisplayName("DAY 는 달력 날짜 차 + 1 — 09:00 시작 방의 둘째 날 08:00 은 DAY2, 시작 전날은 0")
    void givenCalendarDates_whenCalendarDayOf_thenCalendarRule() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 11, 9, 0);
        Room room = Room.createScheduled(start, start.plusHours(56), start.plusHours(59), start.plusHours(60),
                start.plusHours(72), 3);

        assertThat(room.calendarDayOf(LocalDateTime.of(2026, 10, 12, 8, 0))).isEqualTo(2);
        assertThat(room.calendarDayOf(LocalDateTime.of(2026, 10, 11, 8, 0))).isEqualTo(1);
        assertThat(room.calendarDayOf(LocalDateTime.of(2026, 10, 10, 20, 0))).isEqualTo(0);
    }
}
