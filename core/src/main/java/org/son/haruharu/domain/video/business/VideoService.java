package org.son.haruharu.domain.video.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
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
    private final DemoFeedProperties demoFeedProperties;
    private final Clock clock;

    public VideoService(S3StoragePort s3StoragePort, RoomReader roomReader,
                        RoomMemberReader roomMemberReader,
                        VideoPostReader videoPostReader, VideoPostWriter videoPostWriter,
                        ApplicationEventPublisher eventPublisher,
                        DemoFeedProperties demoFeedProperties, Clock clock) {
        this.s3StoragePort = s3StoragePort;
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
        this.videoPostWriter = videoPostWriter;
        this.eventPublisher = eventPublisher;
        this.demoFeedProperties = demoFeedProperties;
        this.clock = clock;
    }

    /** 한 방 멤버의 활성 영상 전체(Day·슬롯 오름차순). 매칭 상대 프로필 카드의 1~3일차 영상용. */
    public List<VideoPost> getMemberVideos(Long roomMemberId) {
        return videoPostReader.findActiveByRoomMember(roomMemberId);
    }

    /**
     * 데모 방 피드 시드(#274). 지정 Day의 모든 슬롯에 대해 각 멤버에게 플레이스홀더 영상을 1개씩 넣는다.
     * 피드 열람 게이트는 "본인이 업로드한 슬롯만 열람"이라, 호출자 본인 멤버까지 포함해 시드해야 리뷰어가 피드를 볼 수 있다.
     * 정상 업로드 경로(시간창·중복·소유권 검증)를 우회하는 데모 전용 시드이므로 video 도메인이 스스로 소유한다.
     * <p>
     * 플레이스홀더 URL은 하드코딩이 아니라 설정({@link DemoFeedProperties})에서 주입한다(#280) — 외부 URL이 죽어도
     * 코드 재배포 없이 env로 교체할 수 있다. 우리 버킷 URL이면 presign 서명, 외부 URL이면 그대로 통과된다.
     */
    @Transactional
    public void seedDemoFeed(List<Long> roomMemberIds, int dayNumber) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<UploadSlot> slots = UploadSlot.ofDay(dayNumber);
        for (Long roomMemberId : roomMemberIds) {
            for (UploadSlot slot : slots) {
                videoPostWriter.save(VideoPost.create(
                        roomMemberId, dayNumber, slot.slotIndex(), null,
                        "데모 일상", demoFeedProperties.videoUrl(), demoFeedProperties.thumbnailUrl(), now));
            }
        }
    }

    /**
     * 데모 리셋 전용 피드 재설정(#267). 지정 멤버들의 기존 영상을 전부 하드삭제한 뒤 지정 Day 피드를 새로 시드한다.
     * 하드삭제는 재시드(advance-day 재진행 포함) 시 이전 Day 영상이 누적·중복되는 것을 막는다.
     */
    @Transactional
    public void resetDemoFeed(List<Long> roomMemberIds, int dayNumber) {
        videoPostWriter.deleteByRoomMemberIds(roomMemberIds);
        seedDemoFeed(roomMemberIds, dayNumber);
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

        // 완주(그 Day 슬롯 전부 채움) 시 구름 보상 지급 트리거. video↔coin 직접 결합을 피해 이벤트로 위임.
        eventPublisher.publishEvent(new VideoUploadedEvent(member.id(), slot.dayNumber()));

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
