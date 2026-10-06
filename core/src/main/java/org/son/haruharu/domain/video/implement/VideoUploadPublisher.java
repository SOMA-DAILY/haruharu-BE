package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 검증한 세 파일을 게시 경로로 복사하고 게시물을 저장한다. 호출자는 멤버 락과 슬롯 중복 검사를 마친
 * 트랜잭션 안에서 호출한다. DB가 롤백되면 복사한 게시 경로를 별도 트랜잭션에서 정리한다.
 */
@Component
@RequiredArgsConstructor
public class VideoUploadPublisher {

    private static final Logger log = LoggerFactory.getLogger(VideoUploadPublisher.class);

    private final VideoUploadAssetVerifier assetVerifier;
    private final VideoPostWriter videoPostWriter;
    private final VideoUploadSessionReader sessionReader;
    private final VideoUploadSessionWriter sessionWriter;
    private final RoomMemberReader roomMemberReader;
    private final S3StoragePort storage;
    private final PlatformTransactionManager transactionManager;

    public VideoPost publish(VideoUploadSession session, LocalDateTime now) {
        deletePublishedAssetsOnRollback(session);
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

    private void deletePublishedAssetsOnRollback(VideoUploadSession session) {
        List<String> publishedKeys = Arrays.stream(VideoUploadVariant.values())
                .map(session::publishedKey)
                .toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // UNKNOWN이면 DB가 커밋됐을 수 있다. 보일 수도 있는 게시물의 파일은 지우지 않는다.
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    TransactionTemplate cleanup = new TransactionTemplate(transactionManager);
                    cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    cleanup.executeWithoutResult(ignored -> {
                        // 재시도는 같은 멤버 락과 같은 게시 경로를 쓴다. 삭제가 끝날 때까지 락을 잡아
                        // 정리 도중 재시도가 먼저 게시하지 못하게 한다.
                        roomMemberReader.lockById(session.memberId());
                        boolean publishedByRetry = sessionReader.findById(session.id())
                                .map(VideoUploadSession::isCompleted)
                                .orElse(false);
                        if (!publishedByRetry) {
                            storage.deleteObjectsOrThrow(publishedKeys);
                        }
                    });
                } catch (RuntimeException failure) {
                    log.atError()
                            .setCause(failure)
                            .addKeyValue("event", "VIDEO_UPLOAD_ROLLBACK_CLEANUP_FAILED")
                            .addKeyValue("uploadSessionId", session.id())
                            .log("업로드 트랜잭션 롤백 후 게시 경로 정리 실패");
                }
            }
        });
    }
}
