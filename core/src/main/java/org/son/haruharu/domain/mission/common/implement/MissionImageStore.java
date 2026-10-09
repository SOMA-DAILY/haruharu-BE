package org.son.haruharu.domain.mission.common.implement;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Component;

/** 미션 사진의 업로드·존재 확인·조회 주소 발급. 사진 목록을 DB 에 따로 두지 않고 S3 객체가 원본이다. */
@Component
@RequiredArgsConstructor
public class MissionImageStore {
    private final S3StoragePort storage;

    public MissionImage upload(byte[] bytes, String contentType) {
        MissionImageFile file = MissionImageFile.validate(bytes, contentType);
        MissionImage image = MissionImage.create(UUID.randomUUID(), file.width(), file.height(), file.extension());
        storage.putImage(image.key(), file.contentType(), bytes);
        return image;
    }

    /** 요청의 image_key 를 미션에 붙일 사진으로 확인한다. 비어 있으면 null, 올리지 않은 키면 거절. */
    public MissionImage require(String key) {
        MissionImage image = MissionImage.parse(key);
        if (image != null && storage.head(image.key()) == null)
            throw new InvalidMissionException("image_key", "업로드한 미션 사진을 선택해 주세요");
        return image;
    }

    /** 비공개 버킷의 단기 조회 주소. 받을 때마다 달라지므로 클라이언트는 key 로 캐시한다. */
    public String signedUrl(MissionImage image) {
        return image == null ? null : storage.createPresignedImageGetUrl(storage.publicBaseUrl() + image.key());
    }
}
