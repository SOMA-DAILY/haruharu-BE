package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VideoUploadSessionTest {
    private final LocalDateTime slot = LocalDateTime.of(2026, 9, 20, 12, 0);
    @Test void givenAcceptedJob_whenCompletedInNextSlot_thenKeepsAcceptedSlot() {
        var job = VideoUploadSession.create(UUID.randomUUID(), 1L, 2L, 3L, slot, slot.plusMinutes(59), "안녕");
        job.requirePending(slot.plusHours(1));
        assertThat(job.completed(42L).slotStart()).isEqualTo(slot);
        assertThat(job.completed(42L).postId()).isEqualTo(42L);
    }
    @Test void givenExpiredJob_whenResumed_thenRejectsWithoutMovingSlot() {
        var job = VideoUploadSession.create(UUID.randomUUID(), 1L, 2L, 3L, slot, slot, null);
        assertThatThrownBy(() -> job.requirePending(slot.plusHours(2))).hasMessageContaining("만료");
    }
    @Test void givenCancelledJob_whenCompleted_thenRejects() {
        var job = VideoUploadSession.create(UUID.randomUUID(), 1L, 2L, 3L, slot, slot, null).cancelled();
        assertThatThrownBy(() -> job.requirePending(slot)).hasMessageContaining("취소");
    }
    @Test void givenUploadSession_whenBuildingObjectKeys_thenOnlyTemporaryAssetsUseLifecyclePrefix() {
        var id = UUID.randomUUID();
        var job = VideoUploadSession.create(id, 1L, 2L, 3L, slot, slot, null);

        assertThat(job.key("viewer")).isEqualTo("users/video-upload-staging/1/videos/" + id + "/viewer.mp4");
        assertThat(job.key("feed")).isEqualTo("users/video-upload-staging/1/videos/" + id + "/feed.mp4");
        assertThat(job.key("thumbnail")).isEqualTo("users/video-upload-staging/1/videos/" + id + "/thumbnail.jpg");
        assertThat(job.publishedKey("viewer")).isEqualTo("users/1/videos/" + id + "/published/viewer.mp4");
        assertThat(job.publishedKey("thumbnail")).isEqualTo("users/1/videos/" + id + "/published/thumbnail.jpg");
    }
}
