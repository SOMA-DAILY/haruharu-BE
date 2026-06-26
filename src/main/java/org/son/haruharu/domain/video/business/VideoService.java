package org.son.haruharu.domain.video.business;

import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.mission.implement.UploadSlotReader;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

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
    private final RoomMemberWriter roomMemberWriter;
    private final UploadSlotReader uploadSlotReader;
    private final MissionReader missionReader;
    private final VideoPostReader videoPostReader;
    private final VideoPostWriter videoPostWriter;
    private final Clock clock;

    public VideoService(S3StoragePort s3StoragePort, RoomReader roomReader,
                        RoomMemberReader roomMemberReader, RoomMemberWriter roomMemberWriter,
                        UploadSlotReader uploadSlotReader, MissionReader missionReader,
                        VideoPostReader videoPostReader, VideoPostWriter videoPostWriter, Clock clock) {
        this.s3StoragePort = s3StoragePort;
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.roomMemberWriter = roomMemberWriter;
        this.uploadSlotReader = uploadSlotReader;
        this.missionReader = missionReader;
        this.videoPostReader = videoPostReader;
        this.videoPostWriter = videoPostWriter;
        this.clock = clock;
    }

    /** 영상 업로드용 presigned PUT URL 발급. 영상은 비공개 prefix(users/{id}/videos/). */
    public VideoUploadUrlResponse createPresignUrl(UUID userId, String filename) {
        String extension = extractExtension(filename);
        String contentType = ALLOWED_VIDEO_TYPES.get(extension);
        if (contentType == null) {
            throw new InvalidVideoExtensionException();
        }
        String key = "users/%s/videos/%s.%s".formatted(userId, UUID.randomUUID(), extension);
        return VideoUploadUrlResponse.from(s3StoragePort.createPresignedPutUrl(key, contentType));
    }

    @Transactional
    public VideoPostResponse upload(UUID userId, UUID roomId, VideoUploadCommand command) {
        RoomMember member = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        if (room.status() != RoomStatus.ACTIVE) {
            throw new RoomNotActiveException();
        }

        UploadSlot slot = uploadSlotReader.getById(command.uploadSlotId());
        if (slot.dayNumber() != room.currentDay()) {
            throw new SlotNotInCurrentDayException();
        }
        int nowHour = LocalTime.now(clock).getHour();
        if (nowHour < slot.startHour() || nowHour >= slot.endHour()) {
            throw new SlotWindowClosedException();
        }
        validateOwnUrl(userId, command.videoUrl());

        Mission mission = command.missionId() == null ? null : missionReader.getById(command.missionId());

        if (videoPostReader.hasActiveUpload(member.id(), slot.id())) {
            throw new DuplicateSlotUploadException();
        }
        // 열쇠 지급 멱등 게이트: 신규 영상 저장 전에 "과거(삭제분 포함) 미션 업로드 여부"를 확정한다.
        boolean alreadyGrantedForSlot = videoPostReader.hasEverUploadedMission(member.id(), slot.id());

        VideoPost saved = videoPostWriter.save(VideoPost.create(
                member.id(), slot.id(), command.missionId(),
                command.caption(), command.videoUrl(), command.thumbnailUrl(), LocalDateTime.now(clock)));

        int grantedPoints = 0;
        int keyBalance = member.pointSum();
        if (mission != null && !alreadyGrantedForSlot) {
            grantedPoints = mission.points();
            keyBalance = roomMemberWriter.save(member.addPoints(grantedPoints)).pointSum();
        }
        return VideoPostResponse.of(saved, grantedPoints, keyBalance);
    }

    @Transactional
    public void delete(UUID userId, UUID videoPostId) {
        VideoPost post = videoPostReader.getById(videoPostId);
        if (post.deletedAt() != null) {
            throw new org.son.haruharu.domain.video.storage.exception.VideoPostNotFoundException();
        }
        RoomMember owner = roomMemberReader.getById(post.roomMemberId());
        if (!owner.userId().equals(userId)) {
            throw new NotVideoOwnerException();
        }
        videoPostWriter.save(post.deletedAt(LocalDateTime.now(clock)));
        // 열쇠는 회수하지 않는다(MVP). 슬롯당 1회 지급이라 재촬영해도 재지급되지 않음.
    }

    private void validateOwnUrl(UUID userId, String videoUrl) {
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
