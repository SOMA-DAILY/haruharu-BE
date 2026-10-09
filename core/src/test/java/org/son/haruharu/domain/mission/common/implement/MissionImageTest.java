package org.son.haruharu.domain.mission.common.implement;

import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionImageException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MissionImageTest {
    static final String KEY = "mission-images/3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b-1080x720.jpg";
    final S3StoragePort storage = mock(S3StoragePort.class);
    final MissionImageStore store = new MissionImageStore(storage);

    static byte[] png(int width, int height) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    @Test
    @DisplayName("서버가 지은 키 이름에서 크기를 읽고, 비어 있으면 사진 없음으로 본다")
    void givenServerIssuedKey_whenParse_thenReadsDimensions() {
        assertThat(MissionImage.parse(KEY)).isEqualTo(new MissionImage(KEY, 1080, 720));
        assertThat(MissionImage.parse(null)).isNull();
        assertThat(MissionImage.parse("  ")).isNull();
        assertThat(MissionImage.create(UUID.fromString("3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b"), 1080, 720, "jpg").key())
                .isEqualTo(KEY);
    }

    @Test
    @DisplayName("미션 사진 경로가 아닌 키(다른 유저의 사진·영상, 경로 이동, 크기 위조)는 거절한다")
    void givenForeignOrMalformedKey_whenParse_thenRejects() {
        for (String key : new String[]{
                "users/7/images/3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b.jpg",
                "mission-images/../users/7/images/a.jpg",
                "mission-images/3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b.jpg",
                "mission-images/3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b-0x720.jpg",
                "mission-images/3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b-1080x720.gif",
                "https://bucket/" + KEY, KEY + "?x=1"}) {
            assertThatThrownBy(() -> MissionImage.parse(key)).as(key).isInstanceOf(InvalidMissionException.class);
        }
    }

    @Test
    @DisplayName("details JSON 의 image 값으로 저장하고 복원한다. 읽을 수 없는 값은 사진 없음")
    void givenImage_whenStoredAndRead_thenRoundTrips() {
        var image = new MissionImage(KEY, 1080, 720);
        assertThat(image.toStored()).isEqualTo(Map.of("key", KEY, "width", 1080, "height", 720));
        assertThat(MissionImage.fromStored(image.toStored())).isEqualTo(image);
        assertThat(MissionImage.fromStored(Map.of("key", KEY, "width", 1080L, "height", 720L))).isEqualTo(image);
        assertThat(MissionImage.fromStored(null)).isNull();
        assertThat(MissionImage.fromStored(Map.of("key", "users/7/a.jpg", "width", 1, "height", 1))).isNull();
        assertThat(MissionImage.fromStored("text")).isNull();
    }

    @Test
    @DisplayName("업로드는 실제 사진을 디코딩해 크기를 키에 담아 올린다")
    void givenRealPng_whenUpload_thenPutsUnderMissionImagesWithDimensions() throws Exception {
        byte[] bytes = png(32, 48);
        MissionImage image = store.upload(bytes, "image/png");
        assertThat(image.width()).isEqualTo(32);
        assertThat(image.height()).isEqualTo(48);
        assertThat(image.key()).startsWith("mission-images/").endsWith("-32x48.png");
        assertThat(MissionImage.parse(image.key())).isEqualTo(image);
        verify(storage).putImage(eq(image.key()), eq("image/png"), eq(bytes));
        // 큰 사진도 원본 크기를 그대로 읽는다(검증용 디코딩만 줄여서 한다).
        assertThat(store.upload(png(4032, 3024), "image/png").key()).endsWith("-4032x3024.png");
    }

    @Test
    @DisplayName("사진을 가장한 파일, 형식 불일치, 크기 초과는 올리지 않는다")
    void givenInvalidFile_whenUpload_thenRejectsWithoutStoring() throws Exception {
        assertThatThrownBy(() -> store.upload("not an image".getBytes(), "image/png"))
                .isInstanceOf(InvalidMissionImageException.class);
        assertThatThrownBy(() -> store.upload(new byte[10 * 1024 * 1024 + 1], "image/png"))
                .isInstanceOf(InvalidMissionImageException.class);
        assertThatThrownBy(() -> store.upload(png(32, 48), "image/jpeg")).isInstanceOf(InvalidMissionImageException.class);
        assertThatThrownBy(() -> store.upload(png(32, 48), "image/gif")).isInstanceOf(InvalidMissionImageException.class);
        assertThatThrownBy(() -> store.upload(new byte[0], "image/png")).isInstanceOf(InvalidMissionImageException.class);
        // 머리말은 정상이지만 본문이 잘린 파일: 디코더의 어떤 예외든 잘못된 사진으로 거절한다.
        byte[] truncated = java.util.Arrays.copyOf(png(64, 64), 60);
        assertThatThrownBy(() -> store.upload(truncated, "image/png")).isInstanceOf(InvalidMissionImageException.class);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("미션에 붙일 때는 실제로 올린 사진인지 확인하고, 다른 경로의 키는 조회조차 하지 않는다")
    void givenKey_whenRequire_thenChecksExistenceOnlyForMissionImages() {
        when(storage.head(KEY)).thenReturn(new S3StoragePort.ObjectMetadata(10, "image/jpeg"));
        assertThat(store.require(KEY)).isEqualTo(new MissionImage(KEY, 1080, 720));
        assertThat(store.require(null)).isNull();
        String missing = KEY.replace("3f2a", "0000");
        assertThatThrownBy(() -> store.require(missing)).isInstanceOf(InvalidMissionException.class);
        assertThatThrownBy(() -> store.require("users/7/images/a.jpg")).isInstanceOf(InvalidMissionException.class);
        verify(storage).head(KEY);
        verify(storage).head(missing);
        verify(storage, org.mockito.Mockito.never()).head("users/7/images/a.jpg");
    }

    @Test
    @DisplayName("조회 주소는 우리 버킷의 미션 사진 객체에 대한 서명 주소다")
    void givenImage_whenSignedUrl_thenSignsBucketObject() {
        when(storage.publicBaseUrl()).thenReturn("https://bucket.example/");
        when(storage.createPresignedImageGetUrl("https://bucket.example/" + KEY)).thenReturn("https://signed");
        assertThat(store.signedUrl(new MissionImage(KEY, 1080, 720))).isEqualTo("https://signed");
        assertThat(store.signedUrl(null)).isNull();
    }
}
