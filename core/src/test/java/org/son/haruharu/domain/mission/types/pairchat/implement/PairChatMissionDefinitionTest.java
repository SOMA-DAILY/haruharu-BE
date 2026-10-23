package org.son.haruharu.domain.mission.types.pairchat.implement;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class PairChatMissionDefinitionTest {
    private final PairChatMissionHandler handler = new PairChatMissionHandler();

    @Test
    @DisplayName("1:1 대화 미션은 유형별 입력이 없어 빈 details 로 저장하고 다시 읽는다")
    void givenPairChatDefinition_whenStoredAndRead_thenEmptyDetailsRoundTrip() {
        var definition = new PairChatMissionDefinition();

        Map<String, Object> stored = handler.toStored(definition);

        assertThat(definition.type()).isEqualTo(MissionType.PAIR_CHAT);
        assertThat(handler.type()).isEqualTo(MissionType.PAIR_CHAT);
        assertThat(stored).isEmpty();
        assertThat(handler.fromStored(stored)).isEqualTo(definition);
        assertThatCode(() -> handler.validate(definition)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("저장된 details 에 모르는 키가 있어도 버리고 읽는다")
    void givenUnknownStoredKeys_whenRead_thenIgnored() {
        assertThat(handler.fromStored(Map.of("content", "옛 값"))).isEqualTo(new PairChatMissionDefinition());
    }

    @Test
    @DisplayName("1:1 대화 미션은 끝 시각이 필수이고 같은 방에서 시간이 겹치면 안 되며 제출을 받지 않는다")
    void givenPairChatRules_thenExplicitCloseExclusiveScheduleAndNoResponse() {
        assertThat(handler.requiresExplicitClose()).isTrue();
        assertThat(handler.exclusiveSchedule()).isTrue();
        assertThat(handler.requiresResponse()).isFalse();
    }

    @Test
    @DisplayName("다른 유형의 정의는 1:1 대화 미션으로 검증·저장하지 않는다")
    void givenOtherTypeDefinition_whenValidated_thenRejected() {
        assertThatThrownBy(() -> handler.validate(new AnnouncementMissionDefinition("안내")))
                .isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> handler.toStored(new AnnouncementMissionDefinition("안내")))
                .isInstanceOf(InvalidMissionException.class);
    }
}
