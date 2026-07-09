package org.son.haruharu.infrastructure.s3;

/**
 * presigned 업로드 발급 결과.
 * - presignedUrl: 클라이언트가 S3로 직접 PUT 하는 임시 URL
 * - objectUrl: 업로드 완료 후 저장/조회에 쓰는 영구 객체 URL
 */
public record PresignedUpload(String presignedUrl, String objectUrl) {}
