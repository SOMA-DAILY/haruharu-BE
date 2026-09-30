package org.son.haruharu.domain.video.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.video.business.dto.VideoPostResponse;
import org.son.haruharu.domain.video.business.dto.VideoUploadAssetResponse;
import org.son.haruharu.domain.video.business.dto.VideoUploadSessionResponse;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.son.haruharu.domain.video.business.exception.DuplicateSlotUploadException;
import org.son.haruharu.domain.video.business.exception.NotVideoOwnerException;
import org.son.haruharu.domain.video.business.exception.RoomNotActiveException;
import org.son.haruharu.domain.video.implement.SlotClock;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoUploadAssetVerifier;
import org.son.haruharu.domain.video.implement.VideoUploadPublisher;
import org.son.haruharu.domain.video.implement.VideoUploadSession;
import org.son.haruharu.domain.video.implement.VideoUploadSessionReader;
import org.son.haruharu.domain.video.implement.VideoUploadSessionWriter;
import org.son.haruharu.domain.video.implement.VideoUploadVariant;
import org.son.haruharu.domain.video.implement.exception.VideoUploadUnavailableException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기 변환 영상 업로드 세션(접수 → 파일별 presign → 게시 → 취소). 한 멤버의 세션 작업과 레거시 업로드는
 * 멤버 행 락으로 직렬화해 슬롯당 게시물 1개를 지킨다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoUploadSessionService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final VideoPostReader videoPostReader;
    private final VideoUploadSessionReader sessionReader;
    private final VideoUploadSessionWriter sessionWriter;
    private final VideoUploadAssetVerifier assetVerifier;
    private final VideoUploadPublisher publisher;
    private final S3StoragePort storage;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** 같은 jobId 재요청은 기존 세션을 그대로 돌려준다(캡션이 다르면 409). 슬롯은 접수 시점에 고정한다. */
    @Transactional
    public VideoUploadSessionResponse reserve(Long userId, Long roomId, UUID jobId, String caption) {
        RoomMember member = lockMember(roomId, userId);
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<VideoUploadSession> previous = sessionReader.findById(jobId);
        if (previous.isPresent()) {
            VideoUploadSession session = requireOwned(previous.get(), userId, roomId);
            if (!Objects.equals(session.caption(), caption)) {
                throw new VideoUploadUnavailableException();
            }
            return VideoUploadSessionResponse.of(session, now);
        }

        Room room = roomReader.findById(roomId).orElseThrow(RoomNotFoundException::new);
        if (room.startsAt() == null || room.statusAt(now) != RoomStatus.ACTIVE) {
            throw new RoomNotActiveException();
        }
        LocalDateTime slotStart = SlotClock.currentSlotStart(room.startsAt(), now);
        if (videoPostReader.hasActiveUpload(member.id(), slotStart)
                || sessionReader.hasPending(member.id(), slotStart, now)) {
            throw new DuplicateSlotUploadException();
        }
        VideoUploadSession session = sessionWriter.insert(
                VideoUploadSession.create(jobId, userId, roomId, member.id(), slotStart, now, caption));
        return VideoUploadSessionResponse.of(session, now);
    }

    @Transactional
    public VideoUploadSessionResponse get(Long userId, Long roomId, UUID id) {
        return VideoUploadSessionResponse.of(loadOwned(userId, roomId, id), LocalDateTime.now(clock));
    }

    /** 이미 검증을 통과한 파일이면 URL 없이 uploaded=true를 돌려준다. */
    @Transactional
    public VideoUploadAssetResponse presign(Long userId, Long roomId, UUID id, String variantValue,
                                            String requestedContentType) {
        VideoUploadSession session = loadOwned(userId, roomId, id);
        session.requirePending(LocalDateTime.now(clock));
        VideoUploadVariant variant = VideoUploadVariant.from(variantValue);
        String contentType = variant.resolveContentType(requestedContentType);
        if (assetVerifier.ready(session, variant)) {
            return VideoUploadAssetResponse.alreadyUploaded();
        }
        return VideoUploadAssetResponse.uploadTo(
                storage.createPresignedPutUrl(session.stagingKey(variant), contentType).presignedUrl());
    }

    /** 응답 유실·동시 완료·게시 후 삭제에도 같은 세션은 처음 게시한 결과만 돌려준다. */
    @Transactional
    public VideoPostResponse complete(Long userId, Long roomId, UUID id) {
        VideoUploadSession session = loadOwned(userId, roomId, id);
        if (session.isCompleted()) {
            return toResponse(videoPostReader.getById(session.postId()));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        session.requirePending(now);
        if (videoPostReader.hasActiveUpload(session.memberId(), session.slotStart())) {
            throw new DuplicateSlotUploadException();
        }
        VideoPost post = publisher.publish(session, now);
        eventPublisher.publishEvent(new VideoUploadedEvent(post.id(), roomId, session.memberId(), post.createdAt()));
        return toResponse(post);
    }

    @Transactional
    public void cancel(Long userId, Long roomId, UUID id) {
        VideoUploadSession session = loadOwned(userId, roomId, id);
        if (session.isCompleted()) {
            throw new VideoUploadUnavailableException();
        }
        sessionWriter.save(session.cancelled());
    }

    /** 멤버십을 확인하고 멤버 행을 잠근다. 같은 멤버의 세션 작업·레거시 업로드는 여기서 줄을 선다. */
    private RoomMember lockMember(Long roomId, Long userId) {
        RoomMember member = roomMemberReader.getByRoomIdAndUserId(roomId, userId);
        roomMemberReader.lockById(member.id());
        return member;
    }

    private VideoUploadSession loadOwned(Long userId, Long roomId, UUID id) {
        lockMember(roomId, userId);
        return requireOwned(sessionReader.getById(id), userId, roomId);
    }

    private VideoUploadSession requireOwned(VideoUploadSession session, Long userId, Long roomId) {
        if (!session.isOwnedBy(userId, roomId)) {
            throw new NotVideoOwnerException();
        }
        return session;
    }

    private VideoPostResponse toResponse(VideoPost post) {
        return VideoPostResponse.of(post,
                storage.createPresignedGetUrl(post.videoUrl()),
                storage.createPresignedGetUrl(post.thumbnailUrl()));
    }
}
