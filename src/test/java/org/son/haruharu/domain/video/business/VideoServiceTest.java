package org.son.haruharu.domain.video.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.mission.implement.UploadSlotReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.video.business.command.VideoUploadCommand;
import org.son.haruharu.domain.video.business.dto.VideoPostResponse;
import org.son.haruharu.domain.video.business.exception.DuplicateSlotUploadException;
import org.son.haruharu.domain.video.business.exception.InvalidVideoExtensionException;
import org.son.haruharu.domain.video.business.exception.NotVideoOwnerException;
import org.son.haruharu.domain.video.business.exception.SlotWindowClosedException;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoPostWriter;
import org.son.haruharu.infrastructure.s3.PresignedUpload;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String BASE = "https://bucket.s3.ap-northeast-2.amazonaws.com/";

    @Mock S3StoragePort s3StoragePort;
    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock UploadSlotReader uploadSlotReader;
    @Mock MissionReader missionReader;
    @Mock VideoPostReader videoPostReader;
    @Mock VideoPostWriter videoPostWriter;

    private VideoService serviceAt(int hour) {
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 6, 26, hour, 0, 0, 0, SEOUL).toInstant(), SEOUL);
        return new VideoService(s3StoragePort, roomReader, roomMemberReader, roomMemberWriter,
                uploadSlotReader, missionReader, videoPostReader, videoPostWriter, clock);
    }

    private RoomMember member(UUID id, UUID userId, int points) {
        return new RoomMember(id, UUID.randomUUID(), userId, "MALE", points, LocalDateTime.now());
    }

    private Room activeDay1(UUID roomId) {
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.now(), 3, 1, LocalDateTime.now(), null);
    }

    // ── presign ──────────────────────────────────────────────

    @Test
    @DisplayName("허용하지 않는 확장자(exe)로 presign 요청 시 예외가 발생한다")
    void givenInvalidExtension_whenPresign_thenThrows() {
        assertThatThrownBy(() -> serviceAt(12).createPresignUrl(UUID.randomUUID(), "malware.exe"))
            .isInstanceOf(InvalidVideoExtensionException.class);
    }

    @Test
    @DisplayName("mp4 presign 요청 시 비공개 videos prefix 키로 발급한다")
    void givenMp4_whenPresign_thenVideosPrefix() {
        UUID userId = UUID.randomUUID();
        given(s3StoragePort.createPresignedPutUrl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("video/mp4")))
            .willReturn(new PresignedUpload("https://put", "https://obj"));

        var result = serviceAt(12).createPresignUrl(userId, "clip.mp4");

        assertThat(result.videoUrl()).isEqualTo("https://obj");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        then(s3StoragePort).should().createPresignedPutUrl(key.capture(), org.mockito.ArgumentMatchers.eq("video/mp4"));
        assertThat(key.getValue()).startsWith("users/" + userId + "/videos/").endsWith(".mp4");
    }

    // ── upload ───────────────────────────────────────────────

    @Test
    @DisplayName("미션 영상 업로드 시 미션 포인트만큼 열쇠가 지급된다(슬롯 최초)")
    void givenMissionUpload_whenUpload_thenGrantsKeysOnce() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        RoomMember me = member(memberId, userId, 0);
        String url = BASE + "users/" + userId + "/videos/v.mp4";
        var command = new VideoUploadCommand(slotId, missionId, url, null, "안녕");

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me);
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeDay1(roomId)));
        given(uploadSlotReader.getById(slotId)).willReturn(new UploadSlot(slotId, 1, 2, 11, 14));
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE);
        given(missionReader.getById(missionId)).willReturn(new Mission(missionId, "미션", 5, true));
        given(videoPostReader.hasActiveUpload(memberId, slotId)).willReturn(false);
        given(videoPostReader.hasEverUploadedMission(memberId, slotId)).willReturn(false);
        given(videoPostWriter.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(roomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        VideoPostResponse result = serviceAt(12).upload(userId, roomId, command);

        assertThat(result.grantedPoints()).isEqualTo(5);
        assertThat(result.keyBalance()).isEqualTo(5);
        then(roomMemberWriter).should().save(any());
    }

    @Test
    @DisplayName("재촬영(삭제 후 재업로드)으로 슬롯에 과거 미션 업로드가 있으면 열쇠를 재지급하지 않는다")
    void givenSlotAlreadyEarned_whenMissionUpload_thenNoRegrant() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        RoomMember me = member(memberId, userId, 5);
        String url = BASE + "users/" + userId + "/videos/v.mp4";
        var command = new VideoUploadCommand(slotId, missionId, url, null, null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me);
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeDay1(roomId)));
        given(uploadSlotReader.getById(slotId)).willReturn(new UploadSlot(slotId, 1, 2, 11, 14));
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE);
        given(missionReader.getById(missionId)).willReturn(new Mission(missionId, "미션", 5, true));
        given(videoPostReader.hasActiveUpload(memberId, slotId)).willReturn(false);
        given(videoPostReader.hasEverUploadedMission(memberId, slotId)).willReturn(true); // 과거 지급 있음
        given(videoPostWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        VideoPostResponse result = serviceAt(12).upload(userId, roomId, command);

        assertThat(result.grantedPoints()).isZero();
        assertThat(result.keyBalance()).isEqualTo(5);
        then(roomMemberWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("미션 없는 업로드는 열쇠를 지급하지 않는다(무료·무보상)")
    void givenNoMission_whenUpload_thenNoGrant() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        RoomMember me = member(memberId, userId, 0);
        String url = BASE + "users/" + userId + "/videos/v.mp4";
        var command = new VideoUploadCommand(slotId, null, url, null, null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me);
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeDay1(roomId)));
        given(uploadSlotReader.getById(slotId)).willReturn(new UploadSlot(slotId, 1, 2, 11, 14));
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE);
        given(videoPostReader.hasActiveUpload(memberId, slotId)).willReturn(false);
        given(videoPostReader.hasEverUploadedMission(memberId, slotId)).willReturn(false);
        given(videoPostWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        VideoPostResponse result = serviceAt(12).upload(userId, roomId, command);

        assertThat(result.grantedPoints()).isZero();
        then(roomMemberWriter).should(never()).save(any());
        then(missionReader).should(never()).getById(any());
    }

    @Test
    @DisplayName("슬롯 시간창 밖(09시, 슬롯 11-14)에서 업로드 시 SlotWindowClosedException")
    void givenOutsideWindow_whenUpload_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        var command = new VideoUploadCommand(slotId, null, BASE + "x", null, null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(UUID.randomUUID(), userId, 0));
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeDay1(roomId)));
        given(uploadSlotReader.getById(slotId)).willReturn(new UploadSlot(slotId, 1, 2, 11, 14));

        assertThatThrownBy(() -> serviceAt(9).upload(userId, roomId, command))
            .isInstanceOf(SlotWindowClosedException.class);
    }

    @Test
    @DisplayName("슬롯에 활성 영상이 이미 있으면 DuplicateSlotUploadException")
    void givenActiveExists_whenUpload_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        String url = BASE + "users/" + userId + "/videos/v.mp4";
        var command = new VideoUploadCommand(slotId, null, url, null, null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(memberId, userId, 0));
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeDay1(roomId)));
        given(uploadSlotReader.getById(slotId)).willReturn(new UploadSlot(slotId, 1, 2, 11, 14));
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE);
        given(videoPostReader.hasActiveUpload(memberId, slotId)).willReturn(true);

        assertThatThrownBy(() -> serviceAt(12).upload(userId, roomId, command))
            .isInstanceOf(DuplicateSlotUploadException.class);
    }

    // ── delete ───────────────────────────────────────────────

    @Test
    @DisplayName("본인 영상은 소프트삭제된다")
    void givenOwner_whenDelete_thenSoftDeletes() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        VideoPost post = new VideoPost(postId, memberId, UUID.randomUUID(), null, null,
                "url", null, false, null, null, LocalDateTime.now());

        given(videoPostReader.getById(postId)).willReturn(post);
        given(roomMemberReader.getById(memberId)).willReturn(member(memberId, userId, 0));
        given(videoPostWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        serviceAt(12).delete(userId, postId);

        ArgumentCaptor<VideoPost> captor = ArgumentCaptor.forClass(VideoPost.class);
        then(videoPostWriter).should().save(captor.capture());
        assertThat(captor.getValue().deletedAt()).isNotNull();
    }

    @Test
    @DisplayName("타인 영상 삭제 시 NotVideoOwnerException")
    void givenNotOwner_whenDelete_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        VideoPost post = new VideoPost(postId, memberId, UUID.randomUUID(), null, null,
                "url", null, false, null, null, LocalDateTime.now());

        given(videoPostReader.getById(postId)).willReturn(post);
        given(roomMemberReader.getById(memberId)).willReturn(member(memberId, UUID.randomUUID(), 0)); // 다른 userId

        assertThatThrownBy(() -> serviceAt(12).delete(userId, postId))
            .isInstanceOf(NotVideoOwnerException.class);
    }
}
