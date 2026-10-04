package org.son.haruharu.domain.mission.business;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.implement.*;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

class MissionImageServiceTest {
    private final MissionImageReader reader = mock(MissionImageReader.class);
    private final MissionImageWriter writer = mock(MissionImageWriter.class);
    private final S3StoragePort storage = mock(S3StoragePort.class);
    private final MissionImageService service = new MissionImageService(reader,writer,storage,new MissionImageUploader(storage,2L));

    @Test @DisplayName("확인된 사진만 업로드하고 방과 운영자 및 원본 키를 보존한다")
    void givenPng_whenUpload_thenStoresImmutableImageAndSignsView() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20,30,BufferedImage.TYPE_INT_RGB),"png",bytes);
        given(writer.save(any())).willAnswer(call -> {
            MissionImage image=call.getArgument(0);
            assertThat(image.roomId()).isEqualTo(1L);
            assertThat(image.createdBy()).isEqualTo(7L);
            assertThat(image.objectKey()).startsWith("users/2/mission-images/1/").endsWith(".png");
            var saved = new MissionImage(8L,image.roomId(),image.createdBy(),image.objectKey(),20,30);
            given(reader.get(8L)).willReturn(saved);
            return saved;
        });
        given(storage.publicBaseUrl()).willReturn("https://test.invalid/");
        given(storage.createPresignedImageGetUrl(anyString())).willReturn("https://test.invalid/signed");
        assertThat(service.upload(1L,7L,bytes.toByteArray(),"image/png").url()).endsWith("/signed");
        then(storage).should().putImage(startsWith("users/2/mission-images/1/"),eq("image/png"),any());
    }

    @Test @DisplayName("잘못된 파일 또는 S3 업로드 실패는 사진 레코드를 만들지 않는다")
    void givenInvalidOrFailedUpload_whenUpload_thenDoesNotPersist() throws Exception {
        assertThatThrownBy(() -> service.upload(1L,7L,new byte[]{1},"image/png")).isInstanceOf(RuntimeException.class);
        then(storage).shouldHaveNoInteractions();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20,30,BufferedImage.TYPE_INT_RGB),"png",bytes);
        willThrow(new IllegalStateException("storage unavailable")).given(storage).putImage(anyString(),anyString(),any());
        assertThatThrownBy(() -> service.upload(1L,7L,bytes.toByteArray(),"image/png")).isInstanceOf(IllegalStateException.class);
        then(writer).shouldHaveNoInteractions();
        assertThat(service.view(null)).isNull();
    }
}
