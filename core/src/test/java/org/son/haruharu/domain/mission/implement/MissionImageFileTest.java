package org.son.haruharu.domain.mission.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

class MissionImageFileTest {
    @Test @DisplayName("실제 PNG 사진은 크기와 형식을 검증한다")
    void givenPng_whenValidate_thenReturnsDimensions() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 48, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = MissionImageFile.validate(bytes.toByteArray(), "image/png");
        assertThat(file.width()).isEqualTo(32);
        assertThat(file.height()).isEqualTo(48);
    }

    @Test @DisplayName("사진을 가장한 파일과 형식 불일치와 크기 초과는 거부한다")
    void givenInvalidFile_whenValidate_thenRejects() throws Exception {
        assertThatThrownBy(() -> MissionImageFile.validate("not an image".getBytes(), "image/png"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> MissionImageFile.validate(new byte[10 * 1024 * 1024 + 1], "image/png"))
                .isInstanceOf(RuntimeException.class);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 48, BufferedImage.TYPE_INT_RGB), "png", bytes);
        assertThatThrownBy(() -> MissionImageFile.validate(bytes.toByteArray(), "image/jpeg"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test @DisplayName("문구 후보는 중복 없이 20개이며 사진 기본값이 없다")
    void givenCatalog_whenRead_thenTwentyUniqueCandidates() {
        assertThat(OperatorMissionTemplate.DEFAULTS).hasSize(20);
        assertThat(OperatorMissionTemplate.DEFAULTS.stream().map(OperatorMissionTemplate::templateId).distinct()).hasSize(20);
    }
}
