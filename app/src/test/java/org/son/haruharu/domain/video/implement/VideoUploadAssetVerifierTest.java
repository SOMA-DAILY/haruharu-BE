package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.son.haruharu.infrastructure.s3.S3StoragePort.ObjectMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.son.haruharu.domain.video.implement.VideoUploadVariant.FEED;
import static org.son.haruharu.domain.video.implement.VideoUploadVariant.THUMBNAIL;
import static org.son.haruharu.domain.video.implement.VideoUploadVariant.VIEWER;

class VideoUploadAssetVerifierTest {

    private final S3StoragePort storage = mock(S3StoragePort.class);
    private final VideoUploadAssetVerifier verifier = new VideoUploadAssetVerifier(storage);
    private final VideoUploadSession job = VideoUploadSession.create(
            UUID.randomUUID(), 1L, 2L, 3L, LocalDateTime.now(), LocalDateTime.now(), null);

    @Test
    void givenQuickTimeCameraOriginal_whenCheckingUploaded_thenAcceptsOnlyViewer() {
        when(storage.head(job.stagingKey(VIEWER))).thenReturn(new ObjectMetadata(1_000_000, "video/quicktime", "mov"));
        when(storage.head(job.stagingKey(FEED))).thenReturn(new ObjectMetadata(1_000_000, "video/quicktime", "mov"));

        assertThat(verifier.ready(job, VIEWER)).isTrue();
        assertThat(verifier.ready(job, FEED)).isFalse();
    }

    @Test
    void givenChangedEtag_whenPromoting_thenFailsWithoutPromotingOtherFiles() {
        when(storage.head(job.stagingKey(VIEWER))).thenReturn(new ObjectMetadata(100, "video/mp4", "original"));
        when(storage.head(job.stagingKey(FEED))).thenReturn(new ObjectMetadata(100, "video/mp4", "feed"));
        when(storage.head(job.stagingKey(THUMBNAIL))).thenReturn(new ObjectMetadata(100, "image/jpeg", "thumbnail"));
        doThrow(new IllegalStateException("conditional copy failed"))
                .when(storage).promote(job.stagingKey(VIEWER), job.publishedKey(VIEWER), "original");

        assertThatThrownBy(() -> verifier.promote(job)).hasMessageContaining("conditional copy failed");

        verify(storage, never()).promote(eq(job.stagingKey(FEED)), anyString(), anyString());
        verify(storage).deleteObjectsOrThrow(
                List.of(job.publishedKey(VIEWER), job.publishedKey(FEED), job.publishedKey(THUMBNAIL)));
    }

    @Test
    void givenInvalidMetadata_whenPromoting_thenRejectsBeforeCopy() {
        List<ObjectMetadata> invalid = List.of(
                new ObjectMetadata(0, "video/mp4", "e"),
                new ObjectMetadata(100, "text/html", "e"),
                new ObjectMetadata(20_000_000, "video/mp4", "e"));
        for (ObjectMetadata meta : invalid) {
            S3StoragePort storage = mock(S3StoragePort.class);
            when(storage.head(anyString())).thenReturn(meta);

            assertThatThrownBy(() -> new VideoUploadAssetVerifier(storage).promote(job)).hasMessageContaining("확인");

            verify(storage, never()).promote(anyString(), anyString(), anyString());
        }
    }

    @Test
    void givenLaterVariantMissing_whenPromoting_thenValidatesEverythingBeforeCopy() {
        when(storage.head(job.stagingKey(VIEWER))).thenReturn(new ObjectMetadata(100, "video/mp4", "viewer-etag"));
        when(storage.head(job.stagingKey(FEED))).thenReturn(new ObjectMetadata(100, "video/mp4", "feed-etag"));
        when(storage.head(job.stagingKey(THUMBNAIL))).thenReturn(null);

        assertThatThrownBy(() -> verifier.promote(job)).hasMessageContaining("확인");

        verify(storage, never()).promote(anyString(), anyString(), anyString());
    }
}
