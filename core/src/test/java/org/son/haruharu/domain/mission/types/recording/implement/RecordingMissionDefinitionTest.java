package org.son.haruharu.domain.mission.types.recording.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;
import org.son.haruharu.domain.mission.common.implement.MissionImage;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition;
import org.son.haruharu.domain.mission.types.profilereveal.implement.ProfileRevealMissionDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingMissionDefinitionTest {
    final RecordingMissionHandler handler = new RecordingMissionHandler();

    @Test
    @DisplayName("촬영 미션은 유형이 RECORDING 이고 서버가 제출을 받지 않는다")
    void givenRecordingMission_whenRead_thenTypeTellsClientsToShowRecording() {
        var definition = new RecordingMissionDefinition("오늘 먹은 점심을 찍어 올려주세요");
        assertThat(handler.type()).isEqualTo(MissionType.RECORDING);
        assertThat(definition.type()).isEqualTo(MissionType.RECORDING);
        assertThat(handler.requiresResponse()).isFalse();
    }

    @Test
    @DisplayName("촬영 주제는 필수이고 500자(이모지 code point)까지 허용한다. 빈 문자열은 null 로 본다")
    void givenMissingOrTooLongContent_whenValidate_thenRejects() {
        assertThat(new RecordingMissionDefinition("  ").content()).isNull();
        assertThatThrownBy(() -> handler.validate(new RecordingMissionDefinition(null)))
                .isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> handler.validate(new RecordingMissionDefinition("🔥".repeat(501))))
                .isInstanceOf(InvalidMissionException.class);
        assertThatCode(() -> handler.validate(new RecordingMissionDefinition("🔥".repeat(500)))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 유형의 정의는 촬영 미션으로 받지 않고, 유형이 다른 정의로는 미션을 만들 수 없다")
    void givenOtherTypeDefinition_whenValidateOrCreate_thenRejects() {
        assertThatThrownBy(() -> handler.validate(new AnnouncementMissionDefinition("안내")))
                .isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> handler.validate(ProfileRevealMissionDefinition.from(List.of("MBTI"))))
                .isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> RoomMission.create(1L, MissionType.RECORDING, new AnnouncementMissionDefinition("안내"),
                LocalDateTime.now(), null, "admin:1")).isInstanceOf(InvalidMissionException.class);
    }

    @Test
    @DisplayName("details JSON 은 content 키로 저장하고 같은 정의로 복원한다")
    void givenDefinition_whenStoredAndRead_thenRoundTrips() {
        var definition = new RecordingMissionDefinition("점심 인증");
        assertThat(handler.toStored(definition)).isEqualTo(Map.of("content", "점심 인증"));
        assertThat(handler.fromStored(handler.toStored(definition))).isEqualTo(definition);
        assertThat(handler.fromStored(Map.of())).isEqualTo(new RecordingMissionDefinition(null));
    }

    @Test
    @DisplayName("사진은 유형 공통 속성이라 촬영 미션에도 선택으로 붙는다")
    void givenRecordingMission_whenCreatedWithOrWithoutImage_thenBothValid() {
        var definition = new RecordingMissionDefinition("점심 인증");
        var image = new MissionImage("mission-images/3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b-1080x720.jpg", 1080, 720);
        var now = LocalDateTime.now();
        assertThat(RoomMission.create(1L, MissionType.RECORDING, definition, image, now, null, "admin:1").image()).isEqualTo(image);
        assertThat(RoomMission.create(1L, MissionType.RECORDING, definition, now, null, "admin:1").image()).isNull();
    }

    @Test
    @DisplayName("촬영 미션의 안내는 본문을 생략할 수 없다")
    void givenRecordingMission_whenAnnouncementWithoutContent_thenNotAllowed() {
        var definition = new RecordingMissionDefinition("점심 인증");
        for (MissionAnnouncementType purpose : MissionAnnouncementType.values()) {
            assertThat(handler.allowsEmptyAnnouncement(purpose, definition)).isFalse();
        }
    }
}
