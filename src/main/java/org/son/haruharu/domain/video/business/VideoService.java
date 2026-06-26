package org.son.haruharu.domain.video.business;

import org.son.haruharu.domain.video.business.dto.VideoUploadUrlResponse;
import org.son.haruharu.domain.video.business.exception.InvalidVideoExtensionException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/** 영상 업로드 라이프사이클(presign 발급 → 업로드 제출 → 재촬영 삭제). */
@Service
public class VideoService {

    private static final Map<String, String> ALLOWED_VIDEO_TYPES = Map.of(
            "mp4", "video/mp4",
            "mov", "video/quicktime",
            "webm", "video/webm");

    private final S3StoragePort s3StoragePort;

    public VideoService(S3StoragePort s3StoragePort) {
        this.s3StoragePort = s3StoragePort;
    }

    /** 영상 업로드용 presigned PUT URL 발급. 영상은 비공개 prefix(users/{id}/videos/). */
    public VideoUploadUrlResponse createPresignUrl(UUID userId, String filename) {
        String extension = extractExtension(filename);
        String contentType = ALLOWED_VIDEO_TYPES.get(extension);
        if (contentType == null) {
            throw new InvalidVideoExtensionException();
        }
        String key = "users/%s/videos/%s.%s".formatted(userId, UUID.randomUUID(), extension);
        return VideoUploadUrlResponse.from(s3StoragePort.createPresignedPutUrl(key, contentType));
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new InvalidVideoExtensionException();
        }
        return filename.substring(dot + 1).toLowerCase();
    }
}
