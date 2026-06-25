package org.son.haruharu.infrastructure.s3;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

@Component
public class S3StorageAdapter implements S3StoragePort {

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
    public String publicBaseUrl() {
        return "https://%s.s3.%s.amazonaws.com/".formatted(
                properties.s3().bucket(), properties.region());
    }

    @Override
    public void deleteObjects(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        List<ObjectIdentifier> objects = keys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();
        s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(properties.s3().bucket())
                .delete(Delete.builder().objects(objects).build())
                .build());
    }
}
