package org.son.haruharu.domain.chat.business.dto;

/** URL은 만료되며 key는 캐시 식별자다. 원본 파일명·파일 지문은 노출하지 않는다. */
public record ChatPhotoView(String key, String url, int width, int height) {}
