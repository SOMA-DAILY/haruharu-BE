package org.son.haruharu.infrastructure.s3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

@Component
public class S3StorageAdapter implements S3StoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3StorageAdapter.class);

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final S3Properties properties;

    public S3StorageAdapter(S3Presigner s3Presigner, S3Client s3Client, S3Properties properties) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public PresignedUpload createPresignedPutUrl(String key, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(properties.s3().bucket())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.s3().presignExpirySeconds()))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        return new PresignedUpload(presigned.url().toString(), publicBaseUrl() + key);
    }

    @Override
    public String createPresignedGetUrl(String objectUrl) {
        return presignGet(objectUrl, properties.s3().videoGetExpirySeconds());
    }

    @Override
    public String createPresignedImageGetUrl(String objectUrl) {
        return presignGet(objectUrl, properties.s3().imageGetExpirySeconds());
    }

    private String presignGet(String objectUrl, long expirySeconds) {
        if (objectUrl == null) {
            return null;
        }
        String base = publicBaseUrl();
        // 우리 버킷 객체(publicBaseUrl prefix)만 서명한다. 그 외(외부·이미 서명된 URL 등)는 그대로 둔다.
        if (!objectUrl.startsWith(base)) {
            return objectUrl;
        }
        String key = objectUrl.substring(base.length());
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.s3().bucket())
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expirySeconds))
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public String publicBaseUrl() {
        return "https://%s.s3.%s.amazonaws.com/".formatted(
                properties.s3().bucket(), properties.region());
    }

    @Override
    public void promote(String sourceKey, String destinationKey, String etag) {
        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(properties.s3().bucket()).sourceKey(sourceKey)
                .destinationBucket(properties.s3().bucket()).destinationKey(destinationKey)
                .copySourceIfMatch(etag).build());
    }

    @Override
    public void putImage(String key, String contentType, byte[] bytes) {
        s3Client.putObject(PutObjectRequest.builder().bucket(properties.s3().bucket()).key(key)
                .contentType(contentType).build(), software.amazon.awssdk.core.sync.RequestBody.fromBytes(bytes));
    }

    @Override
    public ObjectMetadata head(String key) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.s3().bucket()).key(key).build());
            return new ObjectMetadata(response.contentLength(), response.contentType(), response.eTag());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            if (e.statusCode() == 403) {
                // ListBucket 권한이 없으면 S3는 없는 키도 403으로 숨긴다. 어느 쪽이든 게시 검증은 통과할 수 없지만,
                // GetObject 권한 누락 같은 IAM 설정 오류가 클라이언트 400으로만 보이지 않도록 흔적을 남긴다.
                log.atWarn()
                        .addKeyValue("event", "S3_HEAD_FORBIDDEN")
                        .addKeyValue("key", key)
                        .addKeyValue("s3RequestId", e.requestId())
                        .log("S3 HEAD 403: 객체가 없거나 IAM 권한이 부족함");
                return null;
            }
            throw e;
        }
    }

    @Override
    public void deleteObjects(Collection<String> keys) {
        delete(keys);
    }

    @Override
    public void deleteObjectsOrThrow(Collection<String> keys) {
        List<S3Error> errors = delete(keys);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("S3 could not delete " + errors.size() + " object(s)");
        }
    }

    private List<S3Error> delete(Collection<String> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        List<ObjectIdentifier> objects = keys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();
        return s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(properties.s3().bucket())
                .delete(Delete.builder().objects(objects).build())
                .build())
                .errors();
    }
}
