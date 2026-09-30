package org.son.haruharu.domain.video.implement;

import java.util.Arrays;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.implement.exception.VideoUploadIncompleteException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.son.haruharu.infrastructure.s3.S3StoragePort.ObjectMetadata;
import org.springframework.stereotype.Component;

/** 임시 경로에 올라온 세 파일의 메타데이터를 확인하고, 확인한 버전만 게시 경로로 복사한다. */
@Component
@RequiredArgsConstructor
public class VideoUploadAssetVerifier {

    private static final List<VideoUploadVariant> VARIANTS = Arrays.asList(VideoUploadVariant.values());

    private final S3StoragePort storage;

    /** 이미 올라온 파일이면 재업로드 URL을 발급하지 않는다. */
    public boolean ready(VideoUploadSession session, VideoUploadVariant variant) {
        return valid(storage.head(session.stagingKey(variant)), variant);
    }

    /**
     * 세 파일을 모두 확인한 뒤에 복사를 시작한다. 복사는 확인 때의 ETag와 같을 때만 성공하며,
     * 도중에 실패하면 이미 복사한 게시 경로를 지우고 원래 예외를 던진다.
     */
    public void promote(VideoUploadSession session) {
        List<ObjectMetadata> metadata = VARIANTS.stream()
                .map(variant -> {
                    ObjectMetadata meta = storage.head(session.stagingKey(variant));
                    if (!valid(meta, variant) || meta.etag() == null) {
                        throw new VideoUploadIncompleteException();
                    }
                    return meta;
                })
                .toList();
        try {
            for (int index = 0; index < VARIANTS.size(); index++) {
                VideoUploadVariant variant = VARIANTS.get(index);
                storage.promote(session.stagingKey(variant), session.publishedKey(variant), metadata.get(index).etag());
            }
        } catch (RuntimeException failure) {
            try {
                storage.deleteObjectsOrThrow(VARIANTS.stream().map(session::publishedKey).toList());
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private boolean valid(ObjectMetadata metadata, VideoUploadVariant variant) {
        return metadata != null
                && variant.allowsSize(metadata.bytes())
                && variant.allowsContentType(metadata.contentType());
    }
}
