package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Component;

/**
 * 검증한 세 파일을 게시 경로로 복사하고 게시물을 저장한다. 호출자는 멤버 락과 슬롯 중복 검사를 마친
 * 트랜잭션 안에서 호출하며 롤백 시 파일 정리는 Service가 조율한다.
 */
@Component
@RequiredArgsConstructor
public class VideoUploadPublisher {

    private final VideoUploadAssetVerifier assetVerifier;
    private final VideoPostWriter videoPostWriter;
    private final VideoUploadSessionWriter sessionWriter;
    private final S3StoragePort storage;

    public VideoPost publish(VideoUploadSession session, LocalDateTime now) {
        assetVerifier.promote(session);
        String baseUrl = storage.publicBaseUrl();
        VideoPost post = videoPostWriter.save(VideoPost.create(
                        session.memberId(), session.slotStart(), null, session.caption(),
                        baseUrl + session.publishedKey(VideoUploadVariant.VIEWER),
                        baseUrl + session.publishedKey(VideoUploadVariant.THUMBNAIL), now)
                .withFeedVideoUrl(baseUrl + session.publishedKey(VideoUploadVariant.FEED)));
        sessionWriter.save(session.completed(post.id()));
        return post;
    }

}
