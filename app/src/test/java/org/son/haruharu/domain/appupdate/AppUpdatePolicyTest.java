package org.son.haruharu.domain.appupdate;

import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.appupdate.implement.AppUpdatePolicy;
import static org.assertj.core.api.Assertions.*;

class AppUpdatePolicyTest {
    @Test
    void givenBlankOrValidVersions_whenNormalize_thenKeepsOnlyCanonicalNumericVersions() {
        assertThat(AppUpdatePolicy.normalizeVersion(null)).isNull();
        assertThat(AppUpdatePolicy.normalizeVersion(" ")).isNull();
        assertThat(AppUpdatePolicy.normalizeVersion(" 1.2.3 ")).isEqualTo("1.2.3");
        assertThat(AppUpdatePolicy.normalizeVersion("10.20.300")).isEqualTo("10.20.300");
        for (String invalid : new String[]{"1.2", "1.2.3.4", "01.2.3", "v1.2.3", "1.2.3-beta", "-1.2.3", "1000000.0.0"}) {
            assertThatThrownBy(() -> AppUpdatePolicy.normalizeVersion(invalid)).hasMessageContaining("버전");
        }
    }
}
