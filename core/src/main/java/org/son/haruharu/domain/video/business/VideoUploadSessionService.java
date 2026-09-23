package org.son.haruharu.domain.video.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.video.business.dto.VideoPostResponse;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.son.haruharu.domain.video.business.exception.*;
import org.son.haruharu.domain.video.implement.*;
import org.son.haruharu.domain.video.implement.exception.VideoUploadUnavailableException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoUploadSessionService {
    private static final Logger log = LoggerFactory.getLogger(VideoUploadSessionService.class);
    private final VideoUploadSessionRepository sessions;
    private final RoomMemberReader members;
    private final RoomReader rooms;
    private final VideoPostReader posts;
    private final VideoPostWriter writer;
    private final S3StoragePort storage;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final VideoUploadAssetVerifier assets;
    private final PlatformTransactionManager transactionManager;

    public record SessionResponse(UUID id, LocalDateTime slotStart, LocalDateTime expiresAt, Long postId, String status) {}
    public record AssetResponse(String presignedUrl, boolean uploaded) {}

    @Transactional
    public SessionResponse reserve(Long userId, Long roomId, UUID jobId, String caption) {
        var member = members.getByRoomIdAndUserId(roomId, userId);
        sessions.lockMember(member.id());
        var previous = sessions.find(jobId);
        if (previous.isPresent()) {
            var session = owned(previous.get(), userId, roomId);
            if (!Objects.equals(session.caption(), caption)) throw new VideoUploadUnavailableException();
            return response(session);
        }
        var room = rooms.findById(roomId).orElseThrow(RoomNotFoundException::new);
        var now = LocalDateTime.now(clock);
        if (room.startsAt() == null || room.statusAt(now) != RoomStatus.ACTIVE) throw new RoomNotActiveException();
        var slot = SlotClock.currentSlotStart(room.startsAt(), now);
        if (posts.hasActiveUpload(member.id(), slot) || sessions.hasPending(member.id(), slot, now)) {
            throw new DuplicateSlotUploadException();
        }
        return response(sessions.insert(VideoUploadSession.create(jobId,userId,roomId,member.id(),slot,now,caption)));
    }

    @Transactional
    public SessionResponse get(Long userId, Long roomId, UUID id) { return response(load(userId,roomId,id)); }

    @Transactional
    public AssetResponse presign(Long userId, Long roomId, UUID id, String variant) {
        return presign(userId,roomId,id,variant,null);
    }

    @Transactional
    public AssetResponse presign(Long userId, Long roomId, UUID id, String variant, String requestedContentType) {
        var session = load(userId,roomId,id);
        session.requirePending(LocalDateTime.now(clock));
        String type = assets.contentType(variant,requestedContentType);
        if (assets.ready(session,variant)) return new AssetResponse(null,true);
        return new AssetResponse(storage.createPresignedPutUrl(session.key(variant),type).presignedUrl(),false);
    }

    @Transactional
    public VideoPostResponse complete(Long userId, Long roomId, UUID id) {
        var session = load(userId,roomId,id);
        // 응답 유실·동시 완료·삭제 후 재시도에도 원래 게시 결과만 반환한다.
        if (session.postId() != null) return postResponse(posts.getById(session.postId()));
        session.requirePending(LocalDateTime.now(clock));
        if (posts.hasActiveUpload(session.memberId(),session.slotStart())) throw new DuplicateSlotUploadException();
        cleanupPublishedAssetsOnRollback(session);
        assets.promote(session);
        var now = LocalDateTime.now(clock);
        var post = writer.save(VideoPost.create(session.memberId(),session.slotStart(),null,session.caption(),
                storage.publicBaseUrl()+session.publishedKey("viewer"),storage.publicBaseUrl()+session.publishedKey("thumbnail"),now)
                .withFeedVideoUrl(storage.publicBaseUrl()+session.publishedKey("feed")));
        sessions.save(session.completed(post.id()));
        events.publishEvent(new VideoUploadedEvent(post.id(),roomId,session.memberId(),post.createdAt()));
        return postResponse(post);
    }

    private void cleanupPublishedAssetsOnRollback(VideoUploadSession session) {
        var published = List.of(session.publishedKey("viewer"),session.publishedKey("feed"),session.publishedKey("thumbnail"));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                // UNKNOWN may mean the database committed; never delete a possibly visible post.
                if (status != STATUS_ROLLED_BACK) return;
                try {
                    var cleanup = new TransactionTemplate(transactionManager);
                    cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    cleanup.executeWithoutResult(ignored -> {
                        // A retry uses the same member lock and destination keys. Hold the lock
                        // through deletion so a retry cannot publish before this cleanup ends.
                        sessions.lockMember(session.memberId());
                        if (sessions.find(session.id()).map(current -> current.postId() != null).orElse(false)) return;
                        storage.deleteObjects(published);
                    });
                } catch (RuntimeException failure) {
                    log.error("Could not remove published video assets after upload transaction rollback, session={}",session.id(),failure);
                }
            }
        });
    }

    @Transactional
    public void cancel(Long userId, Long roomId, UUID id) {
        var session = load(userId,roomId,id);
        if (session.postId() != null) throw new VideoUploadUnavailableException();
        sessions.save(session.cancelled());
    }

    private VideoUploadSession load(Long userId,Long roomId,UUID id) {
        var member = members.getByRoomIdAndUserId(roomId,userId);
        sessions.lockMember(member.id());
        return owned(sessions.find(id).orElseThrow(VideoUploadUnavailableException::new),userId,roomId);
    }
    private VideoUploadSession owned(VideoUploadSession s,Long userId,Long roomId) {
        if (!s.userId().equals(userId) || !s.roomId().equals(roomId)) throw new NotVideoOwnerException();
        return s;
    }
    private SessionResponse response(VideoUploadSession s) {
        String status = s.postId()!=null ? "complete" : s.isCancelled() ? "cancelled"
                : !LocalDateTime.now(clock).isBefore(s.expiresAt()) ? "expired" : "pending";
        return new SessionResponse(s.id(),s.slotStart(),s.expiresAt(),s.postId(),status);
    }
    private VideoPostResponse postResponse(VideoPost post) {
        return VideoPostResponse.of(post,storage.createPresignedGetUrl(post.videoUrl()),storage.createPresignedGetUrl(post.thumbnailUrl()));
    }
}
