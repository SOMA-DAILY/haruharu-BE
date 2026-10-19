package org.son.haruharu.domain.mission.common.business.dto;

import org.son.haruharu.domain.mission.common.implement.MissionImage;

/** url 은 단기 서명 주소(조회할 때마다 새로 발급). key 는 변하지 않는다. */
public record MissionImageResponse(String key, String url, int width, int height) {
    public static MissionImageResponse of(MissionImage image, String url) {
        return image == null ? null : new MissionImageResponse(image.key(), url, image.width(), image.height());
    }
}
