package org.son.haruharu.infrastructure.s3;

import java.util.Collection;

/** S3 스토리지 포트(이미지·영상 공용). */
public interface S3StoragePort {

    PresignedUpload createPresignedPutUrl(String key, String contentType);

    /**
     * 비공개 객체(영상 등)를 재생·조회할 수 있는 단기 presigned GET URL로 변환한다.
     * objectUrl이 null이면 null을, 우리 버킷(publicBaseUrl) 객체가 아니면 원본을 그대로 반환한다.
     */
    String createPresignedGetUrl(String objectUrl);

    /** 공개 객체 URL 베이스(끝 / 포함). URL 소유권 검증·키 변환에 사용. */
    String publicBaseUrl();

    void deleteObjects(Collection<String> keys);
}
