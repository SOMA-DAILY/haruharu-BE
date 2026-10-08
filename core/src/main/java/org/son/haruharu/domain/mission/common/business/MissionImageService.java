package org.son.haruharu.domain.mission.common.business;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.common.business.dto.MissionImageResponse;
import org.son.haruharu.domain.mission.common.implement.MissionImage;
import org.son.haruharu.domain.mission.common.implement.MissionImageStore;
import org.springframework.stereotype.Service;

/** 미션에 붙일 사진을 먼저 올린다. 돌려준 key 를 미션 생성·수정 요청의 image_key 로 보낸다. */
@Service
@RequiredArgsConstructor
public class MissionImageService {
    private final MissionImageStore images;

    public MissionImageResponse upload(byte[] bytes, String contentType) {
        MissionImage image = images.upload(bytes, contentType);
        return MissionImageResponse.of(image, images.signedUrl(image));
    }
}
