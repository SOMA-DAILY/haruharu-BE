package org.son.haruharu.infrastructure.s3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class S3StorageAdapterTest {

    @Mock S3Presigner s3Presigner;
    @Mock S3Client s3Client;

    S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        // video(3600)와 image(1800) 만료를 다르게 두어 올바른 설정이 선택되는지 검증한다.
        S3Properties properties = new S3Properties(
                "ap-northeast-2", new S3Properties.S3("bucket", 300, 3600, 1800));
        adapter = new S3StorageAdapter(s3Presigner, s3Client, properties);
    }

    @Test
    @DisplayName("createPresignedGetUrl(null)은 null을 반환하고 presigner를 호출하지 않는다")
    void givenNull_whenPresignGet_thenNullAndNoPresign() {
        assertThat(adapter.createPresignedGetUrl(null)).isNull();
        then(s3Presigner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("우리 버킷 객체가 아닌 URL은 서명 없이 원본 그대로 반환한다")
    void givenForeignUrl_whenPresignGet_thenReturnedAsIs() {
        String foreign = "https://other-host.com/users/1/videos/x.mp4";
        assertThat(adapter.createPresignedGetUrl(foreign)).isEqualTo(foreign);
        then(s3Presigner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("createPresignedImageGetUrl(null)은 null을 반환하고 presigner를 호출하지 않는다")
    void givenNull_whenPresignImageGet_thenNullAndNoPresign() {
        assertThat(adapter.createPresignedImageGetUrl(null)).isNull();
        then(s3Presigner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("우리 버킷 객체가 아닌 이미지 URL은 서명 없이 원본 그대로 반환한다")
    void givenForeignUrl_whenPresignImageGet_thenReturnedAsIs() {
        String foreign = "https://other-host.com/users/1/images/x.jpg";
        assertThat(adapter.createPresignedImageGetUrl(foreign)).isEqualTo(foreign);
        then(s3Presigner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("우리 버킷 이미지 URL은 이미지 만료 설정으로 서명한 presigned GET URL을 반환한다")
    void givenOwnImageUrl_whenPresignImageGet_thenSignsWithImageExpiry() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        given(presigned.url()).willReturn(URI.create("https://signed.example/img").toURL());
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).willReturn(presigned);

        String url = adapter.createPresignedImageGetUrl(
                "https://bucket.s3.ap-northeast-2.amazonaws.com/users/1/images/a.jpg");

        assertThat(url).isEqualTo("https://signed.example/img");
        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        then(s3Presigner).should().presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofSeconds(1800));
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("users/1/images/a.jpg");
    }
}
