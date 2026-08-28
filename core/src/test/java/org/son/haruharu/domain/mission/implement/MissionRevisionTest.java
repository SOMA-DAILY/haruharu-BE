package org.son.haruharu.domain.mission.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.implement.exception.InvalidMissionRevisionException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionRevisionTest {

    @Test
    @DisplayName("유효한 추천 메타데이터로 첫 revision을 만든다")
    void givenValidMetadata_whenCreateFirst_thenCreatesRevisionOne() {
        MissionRevision revision = validRevision(false);

        assertThat(revision.revisionNumber()).isEqualTo(1);
        assertThat(revision.description()).isEqualTo("오늘의 풍경을 보여주세요");
        assertThat(revision.baseQualityScore()).isEqualByComparingTo("0.8");
    }

    @Test
    @DisplayName("SAFE_FALLBACK은 모든 안전 메타데이터가 최저 위험이어야 한다")
    void givenUnsafeFallbackMetadata_whenCreate_thenThrows() {
        assertThatThrownBy(() -> MissionRevision.create(
                1L, 1, "조심스러운 이야기를 해주세요",
                MissionAttributeLevel.MEDIUM, MissionGoal.PERSONAL,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "PERSONAL", new BigDecimal("0.7"), true))
                .isInstanceOf(InvalidMissionRevisionException.class);
    }

    @Test
    @DisplayName("semanticCluster는 대문자 식별자만 허용한다")
    void givenLowercaseSemanticCluster_whenCreate_thenThrows() {
        assertThatThrownBy(() -> MissionRevision.create(
                1L, 1, "오늘의 풍경을 보여주세요",
                MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "daily-view", new BigDecimal("0.8"), false))
                .isInstanceOf(InvalidMissionRevisionException.class);
    }

    @Test
    @DisplayName("baseQualityScore는 0 이상 1 이하여야 한다")
    void givenOutOfRangeQualityScore_whenCreate_thenThrows() {
        assertThatThrownBy(() -> MissionRevision.create(
                1L, 1, "오늘의 풍경을 보여주세요",
                MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "DAILY_VIEW", new BigDecimal("1.1"), false))
                .isInstanceOf(InvalidMissionRevisionException.class);
    }

    private MissionRevision validRevision(boolean fallback) {
        return MissionRevision.create(
                1L, 1, "오늘의 풍경을 보여주세요",
                MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "DAILY_VIEW", new BigDecimal("0.8"), fallback);
    }
}
