package org.son.haruharu.domain.mission.types.profilereveal.implement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileRevealMissionDefinitionTest {
    final ProfileRevealMissionHandler handler = new ProfileRevealMissionHandler();

    @Test
    @DisplayName("공개 항목은 중복 없이 표시 순서로 정리하고 입력 목록 변경에 영향받지 않는다")
    void givenMessyInput_whenCreated_thenNormalizes() {
        assertThat(ProfileRevealMissionDefinition.from(List.of("into_now", "MBTI", "MBTI")).revealFields())
                .containsExactly(MissionRevealField.MBTI, MissionRevealField.INTO_NOW);
        var fields = new ArrayList<>(List.of(MissionRevealField.AGE, MissionRevealField.MBTI, MissionRevealField.AGE));
        var definition = new ProfileRevealMissionDefinition(fields);
        fields.clear();
        assertThat(definition.revealFields()).containsExactly(MissionRevealField.MBTI, MissionRevealField.AGE);
    }

    @Test
    @DisplayName("공개 항목은 하나 이상 필요하고 모르는 항목·다른 유형의 정의는 거절한다")
    void givenEmptyUnknownOrOtherType_whenValidate_thenRejects() {
        assertThatThrownBy(() -> handler.validate(ProfileRevealMissionDefinition.from(List.of())))
                .isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> handler.validate(ProfileRevealMissionDefinition.from(null)))
                .isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> ProfileRevealMissionDefinition.from(List.of("XX"))).isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> handler.validate(new AnnouncementMissionDefinition("안내")))
                .isInstanceOf(InvalidMissionException.class);
    }

    @Test
    @DisplayName("details JSON 은 reveal_fields 배열로 저장하고 같은 정의로 복원한다. 모르는 값은 읽을 때 버린다")
    void givenDefinition_whenStoredAndRead_thenRoundTrips() {
        var definition = ProfileRevealMissionDefinition.from(List.of("AGE", "MBTI"));
        assertThat(handler.toStored(definition)).isEqualTo(Map.of("reveal_fields", List.of("MBTI", "AGE")));
        assertThat(handler.fromStored(handler.toStored(definition))).isEqualTo(definition);
        assertThat(handler.fromStored(Map.of("reveal_fields", List.of("MBTI", "REMOVED", 3))))
                .isEqualTo(ProfileRevealMissionDefinition.from(List.of("MBTI")));
        assertThat(handler.fromStored(Map.of())).isEqualTo(ProfileRevealMissionDefinition.from(List.of()));
    }

    @Test
    @DisplayName("공개 미션은 START 안내만 본문 없이 허용한다")
    void givenRevealMission_whenAnnouncementWithoutContent_thenOnlyStartAllowed() {
        var definition = ProfileRevealMissionDefinition.from(List.of("MBTI"));
        assertThat(handler.allowsEmptyAnnouncement(MissionAnnouncementType.START, definition)).isTrue();
        assertThat(handler.allowsEmptyAnnouncement(MissionAnnouncementType.REMINDER, definition)).isFalse();
        assertThat(handler.allowsEmptyAnnouncement(MissionAnnouncementType.RESULT, definition)).isFalse();
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
}
