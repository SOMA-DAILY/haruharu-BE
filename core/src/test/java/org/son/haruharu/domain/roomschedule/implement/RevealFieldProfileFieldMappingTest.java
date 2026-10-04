package org.son.haruharu.domain.roomschedule.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.profile.implement.ProfileField;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** ProfileField 에 항목을 추가하고 RevealField 에 빠뜨리면 그 칸은 편성으로 영영 못 연다. 여기서 잡는다. */
class RevealFieldProfileFieldMappingTest {

    @Test
    @DisplayName("ProfileField 의 모든 항목은 그 칸을 여는 RevealField 가 있고 라벨이 같다")
    void everyProfileFieldHasRevealField() {
        for (ProfileField field : ProfileField.values()) {
            assertThat(Arrays.stream(RevealField.values()).filter(r -> r.profileField() == field).findFirst())
                    .as("RevealField 에 %s 가 없음", field.name())
                    .hasValueSatisfying(r -> assertThat(r.label()).isEqualTo(field.label()));
        }
    }
}
