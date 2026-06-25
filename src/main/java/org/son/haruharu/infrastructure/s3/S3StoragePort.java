package org.son.haruharu.infrastructure.s3;

import java.util.Collection;

/** S3 스토리지 포트(이미지·영상 공용). */
public interface S3StoragePort {

    PresignedUpload createPresignedPutUrl(String key, String contentType);

    /** 공개 객체 URL 베이스(끝 / 포함). URL 소유권 검증·키 변환에 사용. */
    String publicBaseUrl();

    void deleteObjects(Collection<String> keys);
}
