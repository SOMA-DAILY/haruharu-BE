package org.son.haruharu.domain.video.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

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
import org.son.haruharu.domain.video.business.exception.RoomNotActiveException;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.son.haruharu.domain.video.implement.VideoPostWriter;
import org.son.haruharu.infrastructure.s3.PresignedUpload;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String BASE = "https://bucket.s3.ap-northeast-2.amazonaws.com/";
    private static final LocalDate DATE = LocalDate.of(2026, 6, 26);
    private static final LocalDateTime STARTS = DATE.atTime(9, 0);
    // serviceAt(12) 기준 현재 슬롯 = starts_at(09:00) + 3h = 12:00
    private static final LocalDateTime SLOT_AT_12 = DATE.atTime(12, 0);

    @Mock S3StoragePort s3StoragePort;
    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock VideoPostReader videoPostReader;
    @Mock VideoPostWriter videoPostWriter;
    @Mock ApplicationEventPublisher eventPublisher;

    private VideoService serviceAt(int hour) {
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 6, 26, hour, 0, 0, 0, SEOUL).toInstant(), SEOUL);
        return new VideoService(s3StoragePort, roomReader, roomMemberReader,
                videoPostReader, videoPostWriter, eventPublisher, clock);
    }

    private RoomMember member(Long id, Long userId) {
        return new RoomMember(id, 999L, userId, "MALE", null, null, LocalDateTime.now());
    }

    /** starts_at 09:00 새 모델 ACTIVE 방(최종선택 2일 뒤, 종료 3일 뒤). */
    private Room activeRoom(Long roomId) {
        return new Room(roomId, RoomStatus.ACTIVE, DATE, 3, STARTS, null,
                STARTS, STARTS.plusDays(2), STARTS.plusDays(2).plusHours(2),
                STARTS.plusDays(2).plusHours(3), STARTS.plusDays(3));
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
    @DisplayName("정상 업로드 시 서버가 판정한 현재 slot_start로 저장된다(미션·열쇠 없음)")
    void givenValidUpload_whenUpload_thenSavesAtCurrentSlot() {
        Long userId = 1L;
        Long roomId = 10L;
        Long memberId = 20L;
        RoomMember me = member(memberId, userId);
        String url = BASE + "users/" + userId + "/videos/v.mp4";
        var command = new VideoUploadCommand(url, null, "안녕");

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me);
        given(roomReader.findByIdForUpdate(roomId)).willReturn(Optional.of(activeRoom(roomId)));
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE);
        given(videoPostReader.hasActiveUpload(memberId, SLOT_AT_12)).willReturn(false);
        given(videoPostWriter.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(s3StoragePort.createPresignedGetUrl(url)).willReturn("https://signed-get-url");

        VideoPostResponse result = serviceAt(12).upload(userId, roomId, command);

        assertThat(result.slotStart()).isEqualTo(SLOT_AT_12);
        assertThat(result.missionId()).isNull();
        assertThat(result.videoUrl()).isEqualTo("https://signed-get-url"); // 원본(url)이 아니라 서명 URL
        then(eventPublisher).should().publishEvent(new VideoUploadedEvent(
                null, roomId, memberId, LocalDateTime.of(2026, 6, 26, 12, 0)));
    }

    @Test
    @DisplayName("statusAt이 ACTIVE가 아닌 방(시작 전 PENDING)에 업로드 시 RoomNotActiveException")
    void givenNotActiveRoom_whenUpload_thenThrows() {
        Long userId = 1L;
        Long roomId = 11L;
        var command = new VideoUploadCommand(BASE + "users/" + userId + "/videos/v.mp4", null, null);
        // starts_at 20:00 → serviceAt(12)에선 아직 시작 전 → statusAt=PENDING
        LocalDateTime starts = DATE.atTime(20, 0);
        Room pending = new Room(roomId, RoomStatus.PENDING, DATE, 3, null, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(2),
                starts.plusDays(2).plusHours(3), starts.plusDays(3));

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(500L, userId));
        given(roomReader.findByIdForUpdate(roomId)).willReturn(Optional.of(pending));
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE);

        assertThatThrownBy(() -> serviceAt(12).upload(userId, roomId, command))
            .isInstanceOf(RoomNotActiveException.class);
    }

    @Test
    @DisplayName("현재 슬롯에 활성 영상이 이미 있으면 DuplicateSlotUploadException")
    void givenActiveExists_whenUpload_thenThrows() {
        Long userId = 1L;
        Long roomId = 12L;
        Long memberId = 21L;
        String url = BASE + "users/" + userId + "/videos/v.mp4";
        var command = new VideoUploadCommand(url, null, null);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(memberId, userId));
        given(roomReader.findByIdForUpdate(roomId)).willReturn(Optional.of(activeRoom(roomId)));
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE);
        given(videoPostReader.hasActiveUpload(memberId, SLOT_AT_12)).willReturn(true);

        assertThatThrownBy(() -> serviceAt(12).upload(userId, roomId, command))
            .isInstanceOf(DuplicateSlotUploadException.class);
    }

    // ── delete ───────────────────────────────────────────────

    @Test
    @DisplayName("본인 영상은 소프트삭제된다")
    void givenOwner_whenDelete_thenSoftDeletes() {
        Long userId = 1L;
        Long postId = 1L;
        Long memberId = 30L;
        VideoPost post = new VideoPost(postId, memberId, SLOT_AT_12, null, null,
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
        Long postId = 1L;
        Long memberId = 31L;
        VideoPost post = new VideoPost(postId, memberId, SLOT_AT_12, null, null,
                "url", null, false, null, null, LocalDateTime.now());

        given(videoPostReader.getById(postId)).willReturn(post);
        given(roomMemberReader.getById(memberId)).willReturn(member(memberId, 2L)); // 다른 userId

        assertThatThrownBy(() -> serviceAt(12).delete(userId, postId))
            .isInstanceOf(NotVideoOwnerException.class);
    }
}
