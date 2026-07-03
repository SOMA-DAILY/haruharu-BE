package org.son.haruharu.domain.video.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String BASE = "https://bucket.s3.ap-northeast-2.amazonaws.com/";
    // Day1 slot2 = 12-15 시간창 (UploadSlot.DAY1_SLOT2)
    private static final int SLOT_INDEX = 2;

    @Mock S3StoragePort s3StoragePort;
    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock VideoPostReader videoPostReader;
    @Mock VideoPostWriter videoPostWriter;

    private VideoService serviceAt(int hour) {
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 6, 26, hour, 0, 0, 0, SEOUL).toInstant(), SEOUL);
        return new VideoService(s3StoragePort, roomReader, roomMemberReader,
                videoPostReader, videoPostWriter, clock);
    }

    private RoomMember member(UUID id, Long userId) {
        return new RoomMember(id, UUID.randomUUID(), userId, "MALE", null, LocalDateTime.now());
    }

    // startedAt=당일 09:00 앵커 → serviceAt(9/12) 기준 currentDay=1로 파생된다.
    private Room activeDay1(UUID roomId) {
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 26), 3,
                LocalDateTime.of(2026, 6, 26, 9, 0), null);
    }

    // ── presign ──────────────────────────────────────────────

    @Test
    @DisplayName("허용하지 않는 확장자(exe)로 presign 요청 시 예외가 발생한다")
    void givenInvalidExtension_whenPresign_thenThrows() {
        assertThatThrownBy(() -> serviceAt(12).createPresignUrl(1L, "malware.exe"))
            .isInstanceOf(InvalidVideoExtensionException.class);
    }

    @Test
    @DisplayName("mp4 presign 요청 시 비공개 videos prefix 키로 발급한다")
    void givenMp4_whenPresign_thenVideosPrefix() {
        Long userId = 1L;
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
    @DisplayName("정상 업로드 시 현재 Day·슬롯으로 저장된다(미션·열쇠 없음)")
    void givenValidUpload_whenUpload_thenSaves() {
        Long userId = 1L;
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        RoomMember me = member(memberId, userId);
        String url = BASE + "users/" + userId + "/videos/v.mp4";
        var command = new VideoUploadCommand(SLOT_INDEX, url, null, "안녕");

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me);
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeDay1(roomId)));
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE);
        given(videoPostReader.hasActiveUpload(memberId, 1, SLOT_INDEX)).willReturn(false);
        given(videoPostWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        VideoPostResponse result = serviceAt(12).upload(userId, roomId, command);

        assertThat(result.dayNumber()).isEqualTo(1);
        assertThat(result.slotIndex()).isEqualTo(SLOT_INDEX);
        assertThat(result.missionId()).isNull();
    }

    @Test
    @DisplayName("슬롯 시간창 밖(09시, 슬롯 12-15)에서 업로드 시 SlotWindowClosedException")
    void givenOutsideWindow_whenUpload_thenThrows() {
        Long userId = 1L;
        UUID roomId = UUID.randomUUID();
        var command = new VideoUploadCommand(SLOT_INDEX, BASE + "x", null, null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(UUID.randomUUID(), userId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeDay1(roomId)));

        assertThatThrownBy(() -> serviceAt(9).upload(userId, roomId, command))
            .isInstanceOf(SlotWindowClosedException.class);
    }

    @Test
    @DisplayName("슬롯에 활성 영상이 이미 있으면 DuplicateSlotUploadException")
    void givenActiveExists_whenUpload_thenThrows() {
        Long userId = 1L;
        UUID roomId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String url = BASE + "users/" + userId + "/videos/v.mp4";
        var command = new VideoUploadCommand(SLOT_INDEX, url, null, null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(memberId, userId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeDay1(roomId)));
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE);
        given(videoPostReader.hasActiveUpload(memberId, 1, SLOT_INDEX)).willReturn(true);

        assertThatThrownBy(() -> serviceAt(12).upload(userId, roomId, command))
            .isInstanceOf(DuplicateSlotUploadException.class);
    }

    // ── delete ───────────────────────────────────────────────

    @Test
    @DisplayName("본인 영상은 소프트삭제된다")
    void givenOwner_whenDelete_thenSoftDeletes() {
        Long userId = 1L;
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        VideoPost post = new VideoPost(postId, memberId, 1, 1, null, null,
                "url", null, false, null, null, LocalDateTime.now());

        given(videoPostReader.getById(postId)).willReturn(post);
        given(roomMemberReader.getById(memberId)).willReturn(member(memberId, userId));
        given(videoPostWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        serviceAt(12).delete(userId, postId);

        ArgumentCaptor<VideoPost> captor = ArgumentCaptor.forClass(VideoPost.class);
        then(videoPostWriter).should().save(captor.capture());
        assertThat(captor.getValue().deletedAt()).isNotNull();
    }

    @Test
    @DisplayName("타인 영상 삭제 시 NotVideoOwnerException")
    void givenNotOwner_whenDelete_thenThrows() {
        Long userId = 1L;
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        VideoPost post = new VideoPost(postId, memberId, 1, 1, null, null,
                "url", null, false, null, null, LocalDateTime.now());

        given(videoPostReader.getById(postId)).willReturn(post);
        given(roomMemberReader.getById(memberId)).willReturn(member(memberId, 2L)); // 다른 userId

        assertThatThrownBy(() -> serviceAt(12).delete(userId, postId))
            .isInstanceOf(NotVideoOwnerException.class);
    }
}
