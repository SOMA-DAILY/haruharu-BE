package org.son.haruharu.domain.roomschedule.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.profile.implement.ProfileField;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MemberProfileService 가 RevealField.valueOf(ProfileField.name()) 으로 두 enum 을 이름으로 잇는다(#457).
 * ProfileField 에 항목을 추가하고 RevealField 에 빠뜨리면 컴파일은 되고 프로필 시트 조회에서 터지므로 여기서 잡는다.
 */
class RevealFieldProfileFieldMappingTest {

    @Test
    @DisplayName("ProfileField 의 모든 항목은 같은 이름의 RevealField 가 있다")
    void everyProfileFieldHasRevealField() {
        for (ProfileField field : ProfileField.values()) {
            assertThat(RevealField.parse(field.name()))
                    .as("RevealField 에 %s 가 없음", field.name())
                    .isPresent();
        }
    }

    @Test
    @DisplayName("두 enum 의 순서와 라벨이 같다(시트 표시 순서 = 공개 항목 순서)")
    void sameOrderAndLabel() {
        assertThat(Arrays.stream(RevealField.values()).map(Enum::name).toList())
                .isEqualTo(Arrays.stream(ProfileField.values()).map(Enum::name).toList());
        for (ProfileField field : ProfileField.values()) {
            assertThat(RevealField.valueOf(field.name()).label()).isEqualTo(field.label());
        }
    }
}
