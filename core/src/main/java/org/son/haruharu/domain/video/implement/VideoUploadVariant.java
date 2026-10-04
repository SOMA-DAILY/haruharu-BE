package org.son.haruharu.domain.video.implement;

import java.util.Arrays;
import java.util.Set;

import org.son.haruharu.domain.video.implement.exception.InvalidVideoUploadAssetException;

/**
 * 기기 업로드 세션 한 건을 이루는 파일 종류. API 경로의 값({@link #value()})은 FE와의 계약이라 바꾸지 않는다.
 * 서버는 크기와 Content-Type만 확인하며 코덱·해상도는 검사하지 않는다.
 */
public enum VideoUploadVariant {

    /** 몰아보기용 720p 영상. 기기 원본 그대로(QuickTime)도 허용한다. */
    VIEWER("viewer", "viewer.mp4", 10_000_000L, "video/mp4", Set.of("video/mp4", "video/quicktime")),
    /** 피드용 360p 영상. */
    FEED("feed", "feed.mp4", 5_000_000L, "video/mp4", Set.of("video/mp4")),
    /** 첫 프레임 썸네일. */
    THUMBNAIL("thumbnail", "thumbnail.jpg", 2_000_000L, "image/jpeg", Set.of("image/jpeg"));

    private final String value;
    private final String filename;
    private final long maxBytes;
    private final String defaultContentType;
    private final Set<String> allowedContentTypes;

    VideoUploadVariant(String value, String filename, long maxBytes,
                       String defaultContentType, Set<String> allowedContentTypes) {
        this.value = value;
        this.filename = filename;
        this.maxBytes = maxBytes;
        this.defaultContentType = defaultContentType;
        this.allowedContentTypes = allowedContentTypes;
    }

    /** API 경로 값(viewer·feed·thumbnail)을 변환한다. 모르는 값이면 400. */
    public static VideoUploadVariant from(String value) {
        return Arrays.stream(values())
                .filter(variant -> variant.value.equals(value))
                .findFirst()
                .orElseThrow(InvalidVideoUploadAssetException::new);
    }

    /** 요청한 Content-Type(없으면 기본값)을 검증해 presigned PUT에 서명할 값을 돌려준다. */
    public String resolveContentType(String requested) {
        String contentType = requested == null ? defaultContentType : requested;
        if (!allowsContentType(contentType)) {
            throw new InvalidVideoUploadAssetException();
        }
        return contentType;
    }

    public boolean allowsContentType(String contentType) {
        return allowedContentTypes.contains(contentType);
    }

    public boolean allowsSize(long bytes) {
        return bytes > 0 && bytes <= maxBytes;
    }

    public String value() {
        return value;
    }

    public String filename() {
        return filename;
    }
}
