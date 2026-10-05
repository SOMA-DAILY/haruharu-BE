package org.son.haruharu.domain.video.implement;

/** 업로드 세션의 파생 상태. 응답 값({@link #value()})은 FE와의 계약이라 바꾸지 않는다. */
public enum VideoUploadSessionStatus {

    PENDING("pending"),
    COMPLETE("complete"),
    CANCELLED("cancelled"),
    EXPIRED("expired");

    private final String value;

    VideoUploadSessionStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
