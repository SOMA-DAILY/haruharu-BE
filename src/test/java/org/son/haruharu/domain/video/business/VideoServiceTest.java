package org.son.haruharu.domain.video.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.video.business.dto.VideoUploadUrlResponse;
import org.son.haruharu.domain.video.business.exception.InvalidVideoExtensionException;
import org.son.haruharu.infrastructure.s3.PresignedUpload;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock S3StoragePort s3StoragePort;

    VideoService videoService;

    @BeforeEach
    void setUp() {
        videoService = new VideoService(s3StoragePort);
    }

    @Test
    @DisplayName("허용 확장자(mp4)로 presign 요청 시 비공개 videos prefix 키로 URL을 발급한다")
    void givenMp4_whenPresign_thenVideosPrefixKey() {
        UUID userId = UUID.randomUUID();
        given(s3StoragePort.createPresignedPutUrl(org.mockito.ArgumentMatchers.anyString(), eq("video/mp4")))
            .willReturn(new PresignedUpload("https://put", "https://obj"));

        VideoUploadUrlResponse result = videoService.createPresignUrl(userId, "clip.MP4");

        assertThat(result.presignedUrl()).isEqualTo("https://put");
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        then(s3StoragePort).should().createPresignedPutUrl(keyCaptor.capture(), eq("video/mp4"));
        assertThat(keyCaptor.getValue()).startsWith("users/" + userId + "/videos/").endsWith(".mp4");
    }

    @Test
    @DisplayName("허용하지 않는 확장자(exe)로 presign 요청 시 예외가 발생한다")
    void givenInvalidExtension_whenPresign_thenThrows() {
        assertThatThrownBy(() -> videoService.createPresignUrl(UUID.randomUUID(), "malware.exe"))
            .isInstanceOf(InvalidVideoExtensionException.class);
    }
}
