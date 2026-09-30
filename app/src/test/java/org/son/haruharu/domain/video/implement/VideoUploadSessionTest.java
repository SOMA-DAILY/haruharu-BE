package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoUploadSessionTest {

    private final LocalDateTime slot = LocalDateTime.of(2026, 9, 20, 12, 0);

    @Test
    void givenAcceptedJob_whenCompletedInNextSlot_thenKeepsAcceptedSlot() {
        VideoUploadSession job = VideoUploadSession.create(UUID.randomUUID(), 1L, 2L, 3L, slot, slot.plusMinutes(59), "안녕");

        job.requirePending(slot.plusHours(1));

        assertThat(job.completed(42L).slotStart()).isEqualTo(slot);
        assertThat(job.completed(42L).postId()).isEqualTo(42L);
    }

    @Test
    void givenExpiredJob_whenResumed_thenRejectsWithoutMovingSlot() {
        VideoUploadSession job = VideoUploadSession.create(UUID.randomUUID(), 1L, 2L, 3L, slot, slot, null);

        assertThatThrownBy(() -> job.requirePending(slot.plusHours(2))).hasMessageContaining("만료");
    }

    @Test
    void givenCancelledJob_whenCompleted_thenRejects() {
        VideoUploadSession job = VideoUploadSession.create(UUID.randomUUID(), 1L, 2L, 3L, slot, slot, null).cancelled();

        assertThatThrownBy(() -> job.requirePending(slot)).hasMessageContaining("취소");
    }

    @Test
    void givenSessionLifecycle_whenReadingStatus_thenCompletionWinsOverExpiry() {
        VideoUploadSession job = VideoUploadSession.create(UUID.randomUUID(), 1L, 2L, 3L, slot, slot, null);

        assertThat(job.status(slot.plusMinutes(119))).isEqualTo(VideoUploadSessionStatus.PENDING);
        assertThat(job.status(slot.plusHours(2))).isEqualTo(VideoUploadSessionStatus.EXPIRED);
        assertThat(job.cancelled().status(slot)).isEqualTo(VideoUploadSessionStatus.CANCELLED);
        assertThat(job.completed(42L).status(slot.plusHours(3))).isEqualTo(VideoUploadSessionStatus.COMPLETE);
        assertThat(VideoUploadSessionStatus.COMPLETE.value()).isEqualTo("complete");
    }

    @Test
    void givenUploadSession_whenBuildingObjectKeys_thenOnlyTemporaryAssetsUseLifecyclePrefix() {
        UUID id = UUID.randomUUID();
        VideoUploadSession job = VideoUploadSession.create(id, 1L, 2L, 3L, slot, slot, null);

        assertThat(job.stagingKey(VideoUploadVariant.VIEWER))
                .isEqualTo("users/video-upload-staging/1/videos/" + id + "/viewer.mp4");
        assertThat(job.stagingKey(VideoUploadVariant.FEED))
                .isEqualTo("users/video-upload-staging/1/videos/" + id + "/feed.mp4");
        assertThat(job.stagingKey(VideoUploadVariant.THUMBNAIL))
                .isEqualTo("users/video-upload-staging/1/videos/" + id + "/thumbnail.jpg");
        assertThat(job.publishedKey(VideoUploadVariant.VIEWER))
                .isEqualTo("users/1/videos/" + id + "/published/viewer.mp4");
        assertThat(job.publishedKey(VideoUploadVariant.THUMBNAIL))
                .isEqualTo("users/1/videos/" + id + "/published/thumbnail.jpg");
    }
}
