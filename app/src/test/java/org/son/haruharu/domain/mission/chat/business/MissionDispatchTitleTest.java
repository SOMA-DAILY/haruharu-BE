package org.son.haruharu.domain.mission.chat.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.common.implement.MissionType;

import static org.assertj.core.api.Assertions.assertThat;

class MissionDispatchTitleTest {

    @Test
    @DisplayName("운영 글 푸시 제목: 촬영 미션은 [미션], 그 밖의 안내는 [공지]를 붙이고 이미 있으면 두 번 붙이지 않는다(#505)")
    void givenMissionType_whenTagged_thenPrefixOnce() {
        assertThat(MissionDispatchService.tagged(MissionType.RECORDING, "🎬 하루의 새 미션")).isEqualTo("[미션] 🎬 하루의 새 미션");
        assertThat(MissionDispatchService.tagged(MissionType.ANNOUNCEMENT, "📢 하루 공지")).isEqualTo("[공지] 📢 하루 공지");
        assertThat(MissionDispatchService.tagged(MissionType.PROFILE_REVEAL, "💙 최종 선택 시작")).isEqualTo("[공지] 💙 최종 선택 시작");
        assertThat(MissionDispatchService.tagged(MissionType.ANNOUNCEMENT, "[공지] 오늘 일정")).isEqualTo("[공지] 오늘 일정");
        assertThat(MissionDispatchService.tagged(MissionType.RECORDING, "[공지] 촬영 안내")).isEqualTo("[공지] 촬영 안내");
    }
}
