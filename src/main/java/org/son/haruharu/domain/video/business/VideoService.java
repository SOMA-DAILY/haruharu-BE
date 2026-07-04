package org.son.haruharu.domain.video.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import org.son.haruharu.domain.mission.implement.UploadSlot;
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
import org.son.haruharu.domain.video.business.exception.SlotNotInCurrentDayException;
import org.son.haruharu.domain.video.business.exception.SlotWindowClosedException;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoPostWriter;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 영상 업로드 라이프사이클(presign 발급 → 업로드 제출 → 재촬영 삭제). */
@Service
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
    private final Clock clock;

    public VideoService(S3StoragePort s3StoragePort, RoomReader roomReader,
                        RoomMemberReader roomMemberReader,
                        VideoPostReader videoPostReader, VideoPostWriter videoPostWriter, Clock clock) {
        this.s3StoragePort = s3StoragePort;
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
        this.videoPostWriter = videoPostWriter;
        this.clock = clock;
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
        if (room.status() != RoomStatus.ACTIVE) {
            throw new RoomNotActiveException();
        }

        UploadSlot slot = UploadSlot.of(
                        room.currentDayAt(LocalDateTime.now(clock), UploadSlot.dayStartHour()), command.slotIndex())
                .orElseThrow(SlotNotInCurrentDayException::new);
        int nowHour = LocalTime.now(clock).getHour();
        if (!slot.isOpenAt(nowHour)) {
            throw new SlotWindowClosedException();
        }
        validateOwnUrl(userId, command.videoUrl());

        if (videoPostReader.hasActiveUpload(member.id(), slot.dayNumber(), slot.slotIndex())) {
            throw new DuplicateSlotUploadException();
        }

        // MVP: 미션 비활성화 — mission_id는 항상 null. 세션 재화(열쇠) 제거됨.
        VideoPost saved = videoPostWriter.save(VideoPost.create(
                member.id(), slot.dayNumber(), slot.slotIndex(), null,
                command.caption(), command.videoUrl(), command.thumbnailUrl(), LocalDateTime.now(clock)));

        return VideoPostResponse.of(saved);
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
