package org.son.haruharu.domain.mission.types.announcement.implement;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementContent;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.mission.types.profilereveal.implement.ProfileRevealMissionDefinition;
import org.son.haruharu.domain.room.implement.Room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnouncementMissionDefinitionTest {
    final AnnouncementMissionHandler handler = new AnnouncementMissionHandler();

    @Test
    @DisplayName("안내형은 본문이 필수이고 500자(이모지 code point)까지 허용한다. 빈 문자열은 null 로 본다")
    void givenMissingOrTooLongContent_whenValidate_thenRejects() {
        assertThat(new AnnouncementMissionDefinition("  ").content()).isNull();
        assertThat(MissionAnnouncementContent.text("  ")).isNull();
        assertThatThrownBy(() -> handler.validate(new AnnouncementMissionDefinition(null)))
                .isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> handler.validate(new AnnouncementMissionDefinition("🔥".repeat(501))))
                .isInstanceOf(InvalidMissionException.class);
        assertThatCode(() -> handler.validate(new AnnouncementMissionDefinition("🔥".repeat(500)))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 유형의 정의는 안내형으로 받지 않는다")
    void givenOtherTypeDefinition_whenValidate_thenRejects() {
        assertThatThrownBy(() -> handler.validate(ProfileRevealMissionDefinition.from(java.util.List.of("MBTI"))))
                .isInstanceOf(InvalidMissionException.class);
    }

    @Test
    @DisplayName("details JSON 은 content 키로 저장하고 같은 정의로 복원한다")
    void givenDefinition_whenStoredAndRead_thenRoundTrips() {
        var definition = new AnnouncementMissionDefinition("오늘의 대화를 시작해 주세요");
        assertThat(handler.toStored(definition)).isEqualTo(Map.of("content", "오늘의 대화를 시작해 주세요"));
        assertThat(handler.fromStored(handler.toStored(definition))).isEqualTo(definition);
        assertThat(handler.fromStored(Map.of("content", 1))).isEqualTo(new AnnouncementMissionDefinition(null));
    }

    @Test
    @DisplayName("안내형 미션의 안내는 본문을 생략할 수 없고, 본문·푸시 길이의 기존 검증과 이모지 글자 수를 보존한다")
    void givenInvalidAnnouncement_whenValidated_thenFieldErrors() {
        var now = LocalDateTime.now();
        var definition = new AnnouncementMissionDefinition("안내");
        var mission = RoomMission.create(1L, MissionType.ANNOUNCEMENT, definition, now, null, "admin:1");
        assertThat(handler.allowsEmptyAnnouncement(MissionAnnouncementType.START, definition)).isFalse();
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
