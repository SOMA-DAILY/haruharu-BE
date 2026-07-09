package org.son.haruharu.infrastructure.s3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class S3StorageAdapterTest {

    @Mock S3Presigner s3Presigner;
    @Mock S3Client s3Client;

    S3StorageAdapter adapter;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties(
                "ap-northeast-2", new S3Properties.S3("bucket", 300, 3600));
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
}
