package org.son.haruharu.domain.video.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.video.business.command.VideoUploadCommand;
import org.son.haruharu.domain.video.business.dto.VideoPostResponse;
import org.son.haruharu.domain.video.business.dto.VideoUploadUrlResponse;
import org.son.haruharu.domain.video.business.exception.DuplicateSlotUploadException;
import org.son.haruharu.domain.video.business.exception.InvalidVideoExtensionException;
import org.son.haruharu.domain.video.business.exception.InvalidVideoUrlException;
import org.son.haruharu.domain.video.business.exception.NotVideoOwnerException;
import org.son.haruharu.domain.video.business.exception.RoomNotActiveException;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.son.haruharu.domain.video.implement.SlotClock;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoPostWriter;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 영상 업로드 라이프사이클(presign 발급 → 업로드 제출 → 재촬영 삭제). */
@Service
@Transactional(readOnly = true)
public class VideoService {

    private static final Map<String, String> ALLOWED_VIDEO_TYPES = Map.of(
            "mp4", "video/mp4",
            "mov", "video/quicktime",
            "webm", "video/webm");

    private final S3StoragePort s3StoragePort;
    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final VideoPostReader videoPostReader;
    private final VideoPostWriter videoPostWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public VideoService(S3StoragePort s3StoragePort, RoomReader roomReader,
                        RoomMemberReader roomMemberReader,
                        VideoPostReader videoPostReader, VideoPostWriter videoPostWriter,
                        ApplicationEventPublisher eventPublisher, Clock clock) {
        this.s3StoragePort = s3StoragePort;
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
        this.videoPostWriter = videoPostWriter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 한 방 멤버의 활성 영상 전체(slot_start 오름차순). 매칭 상대 프로필 카드의 영상 목록용. */
    public List<VideoPost> getMemberVideos(Long roomMemberId) {
        return videoPostReader.findActiveByRoomMember(roomMemberId);
    }

    /** 영상 업로드용 presigned PUT URL 발급. 영상은 비공개 prefix(users/{id}/videos/). */
    public VideoUploadUrlResponse createPresignUrl(Long userId, String filename) {
        String extension = extractExtension(filename);
        String contentType = ALLOWED_VIDEO_TYPES.get(extension);
        if (contentType == null) {
            throw new InvalidVideoExtensionException();
        }
        String key = "users/%s/videos/%s.%s".formatted(userId, UUID.randomUUID(), extension);
        return VideoUploadUrlResponse.from(s3StoragePort.createPresignedPutUrl(key, contentType));
    }

    @Transactional
    public VideoPostResponse upload(Long userId, Long roomId, VideoUploadCommand command) {
        RoomMember member = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        // 시각 파생 상태로 판정(저장 status는 배치 지연될 수 있음). 새 모델(starts_at) 방만 업로드 대상.
        if (room.startsAt() == null || room.statusAt(now) != RoomStatus.ACTIVE) {
            throw new RoomNotActiveException();
        }

        // 슬롯은 서버가 판정하는 "현재 슬롯"(starts_at 상대 정시). 클라는 슬롯을 지정하지 않는다.
        LocalDateTime slotStart = SlotClock.currentSlotStart(room.startsAt(), now);
        validateOwnUrl(userId, command.videoUrl());

        if (videoPostReader.hasActiveUpload(member.id(), slotStart)) {
            throw new DuplicateSlotUploadException();
        }

        // MVP: 미션 비활성화 — mission_id는 항상 null.
        VideoPost saved = videoPostWriter.save(VideoPost.create(
                member.id(), slotStart, null,
                command.caption(), command.videoUrl(), command.thumbnailUrl(), now));
        eventPublisher.publishEvent(new VideoUploadedEvent(saved.id(), roomId, member.id(), saved.createdAt()));

        // 비공개 영상은 서명 없이 접근 불가(403). 재생 가능한 presigned GET URL로 변환해 응답한다(#162).
        return VideoPostResponse.of(saved,
                s3StoragePort.createPresignedGetUrl(saved.videoUrl()),
                s3StoragePort.createPresignedGetUrl(saved.thumbnailUrl()));
    }

    @Transactional
    public void delete(Long userId, Long videoPostId) {
        VideoPost post = videoPostReader.getById(videoPostId);
        if (post.deletedAt() != null) {
            throw new org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException();
        }
        RoomMember owner = roomMemberReader.getById(post.roomMemberId());
        if (!owner.userId().equals(userId)) {
            throw new NotVideoOwnerException();
        }
        videoPostWriter.save(post.deletedAt(LocalDateTime.now(clock)));
        // MVP: 미션 비활성화로 열쇠 지급 자체가 없어 회수 로직도 없다.
    }

    private void validateOwnUrl(Long userId, String videoUrl) {
        String ownPrefix = s3StoragePort.publicBaseUrl() + "users/" + userId + "/videos/";
        if (videoUrl == null || !videoUrl.startsWith(ownPrefix)) {
            throw new InvalidVideoUrlException();
        }
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new InvalidVideoExtensionException();
        }
        return filename.substring(dot + 1).toLowerCase();
    }
}
