package org.son.haruharu.domain.video.business.dto;

/** 이미 올라온 파일이면 uploaded=true, presignedUrl=null. */
public record VideoUploadAssetResponse(String presignedUrl, boolean uploaded) {

    public static VideoUploadAssetResponse alreadyUploaded() {
        return new VideoUploadAssetResponse(null, true);
    }

    public static VideoUploadAssetResponse uploadTo(String presignedUrl) {
        return new VideoUploadAssetResponse(presignedUrl, false);
    }
}
