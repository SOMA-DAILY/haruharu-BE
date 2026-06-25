package org.son.haruharu.domain.profile.business.dto;

import org.son.haruharu.infrastructure.s3.PresignedUpload;

public record ImageUploadUrlResponse(String presignedUrl, String imageUrl) {

    public static ImageUploadUrlResponse from(PresignedUpload upload) {
        return new ImageUploadUrlResponse(upload.presignedUrl(), upload.objectUrl());
    }
}
