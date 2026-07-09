package org.son.haruharu.domain.video.business.dto;

import org.son.haruharu.infrastructure.s3.PresignedUpload;

public record VideoUploadUrlResponse(String presignedUrl, String videoUrl) {

    public static VideoUploadUrlResponse from(PresignedUpload upload) {
        return new VideoUploadUrlResponse(upload.presignedUrl(), upload.objectUrl());
    }
}
