package org.son.haruharu.domain.chat.implement;

/** 미션 말풍선에 바로 그릴 사진. url 은 단기 서명 주소라 저장하지 않고, 캐시는 key 로 한다. */
public record ChatMissionImage(String key, String url, int width, int height) {
}
