package org.son.haruharu.domain.chat.implement;

/** 저장된 사진. 파일 지문은 같은 요청 ID의 다른 사진을 거절할 때만 쓴다. */
public record ChatPhoto(String key, int width, int height, String digest) {}
