package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InteractionGuidanceProgressTest {
    private final LocalDateTime at = LocalDateTime.of(2026, 10, 23, 9, 0);

    @Test
    @DisplayName("아직 두 행동을 하지 않았으면 반응 안내부터 고른다")
    void givenNoActions_whenNextGuide_thenReactionFirst() {
        assertThat(InteractionGuidanceProgress.empty().nextGuide()).isEqualTo(InteractionGuideKind.REACTION);
    }

    @Test
    @DisplayName("반응을 성공했거나 안내를 소비했다면 답장을 안내한다")
    void givenReactionDoneOrConsumed_whenNextGuide_thenReply() {
        assertThat(new InteractionGuidanceProgress(at, null, null, null).nextGuide())
                .isEqualTo(InteractionGuideKind.REPLY);
        assertThat(new InteractionGuidanceProgress(null, null, at, null).nextGuide())
                .isEqualTo(InteractionGuideKind.REPLY);
    }

    @Test
    @DisplayName("두 안내를 소비했거나 행동을 마쳤으면 다음 안내가 없다")
    void givenBothCompletedOrConsumed_whenNextGuide_thenNone() {
        assertThat(new InteractionGuidanceProgress(at, at, null, null).nextGuide()).isNull();
        assertThat(new InteractionGuidanceProgress(null, null, at, at).nextGuide()).isNull();
    }
}
