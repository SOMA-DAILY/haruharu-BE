package org.son.haruharu.domain.mission.types.announcement.implement;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;

import static org.assertj.core.api.Assertions.*;

class AnnouncementMissionTest {
    @Test
    void givenAnnouncementDetails_whenNormalize_thenOwnsContentAndRevealRules() {
        var fields = new ArrayList<>(List.of(MissionRevealField.AGE, MissionRevealField.MBTI, MissionRevealField.AGE));
        var definition = new AnnouncementMissionDefinition("  ", fields);
        fields.clear();
        var mission = AnnouncementMission.create(10L, definition);
        assertThat(mission.definition().content()).isNull();
        assertThat(mission.definition().revealFields()).containsExactly(MissionRevealField.MBTI, MissionRevealField.AGE);
        assertThat(mission.roomMissionId()).isEqualTo(10L);
        assertThatCode(definition::validate).doesNotThrowAnyException();
    }

    @Test
    void givenMissingOrTooLongAnnouncement_whenValidate_thenRejectInSubtype() {
        assertThatThrownBy(() -> new AnnouncementMissionDefinition(null, List.of()).validate())
                .isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> new AnnouncementMissionDefinition("🔥".repeat(501), List.of()).validate())
                .isInstanceOf(InvalidMissionException.class);
        assertThatCode(() -> new AnnouncementMissionDefinition("🔥".repeat(500), List.of()).validate()).doesNotThrowAnyException();
        assertThatThrownBy(() -> AnnouncementMission.create(0L, new AnnouncementMissionDefinition("안내", List.of())))
                .isInstanceOf(InvalidMissionException.class);
    }

    @Test
    void givenStoredAnnouncement_whenChangeDefinition_thenPreserveIdentityAndAudit() {
        var created = LocalDateTime.of(2026,10,7,10,0);
        var first = new AnnouncementMission(10L, new AnnouncementMissionDefinition("원문", List.of()), created, created);
        var changed = first.withDefinition(new AnnouncementMissionDefinition("수정", List.of(MissionRevealField.AGE)));
        assertThat(changed.roomMissionId()).isEqualTo(first.roomMissionId());
        assertThat(changed.createdAt()).isEqualTo(created);
        assertThat(changed.definition().content()).isEqualTo("수정");
        assertThat(first.definition().content()).isEqualTo("원문");
    }
}
