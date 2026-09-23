package org.son.haruharu.domain.video.implement;
import org.junit.jupiter.api.Test;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class VideoUploadAssetVerifierTest {
    @Test void givenQuickTimeCameraOriginal_whenCheckingViewer_thenAcceptsOnlyViewerVariant() {
        var storage=mock(S3StoragePort.class);
        var job=VideoUploadSession.create(UUID.randomUUID(),1L,2L,3L,LocalDateTime.now(),LocalDateTime.now(),null);
        when(storage.head(job.key("viewer"))).thenReturn(new S3StoragePort.ObjectMetadata(1_000_000,"video/quicktime","mov"));
        when(storage.head(job.key("feed"))).thenReturn(new S3StoragePort.ObjectMetadata(1_000_000,"video/quicktime","mov"));
        var verifier=new VideoUploadAssetVerifier(storage);
        assertThat(verifier.ready(job,"viewer")).isTrue();
        assertThat(verifier.ready(job,"feed")).isFalse();
        assertThat(verifier.contentType("viewer")).isEqualTo("video/mp4");
        assertThat(verifier.contentType("viewer","video/quicktime")).isEqualTo("video/quicktime");
        assertThatThrownBy(()->verifier.contentType("feed","video/quicktime")).hasMessageContaining("확인");
        assertThatThrownBy(()->verifier.contentType("viewer","video/webm")).hasMessageContaining("확인");
    }

    @Test void givenChangedEtag_whenPromoting_thenFailsWithoutPromotingOtherFiles() {
        var storage=mock(S3StoragePort.class);
        var job=VideoUploadSession.create(UUID.randomUUID(),1L,2L,3L,LocalDateTime.now(),LocalDateTime.now(),null);
        when(storage.head(job.key("viewer"))).thenReturn(new S3StoragePort.ObjectMetadata(100,"video/mp4","original"));
        when(storage.head(job.key("feed"))).thenReturn(new S3StoragePort.ObjectMetadata(100,"video/mp4","feed"));
        when(storage.head(job.key("thumbnail"))).thenReturn(new S3StoragePort.ObjectMetadata(100,"image/jpeg","thumbnail"));
        doThrow(new IllegalStateException("conditional copy failed")).when(storage).promote(job.key("viewer"),job.publishedKey("viewer"),"original");
        assertThatThrownBy(()->new VideoUploadAssetVerifier(storage).promote(job)).hasMessageContaining("conditional copy failed");
        verify(storage,never()).promote(eq(job.key("feed")),anyString(),anyString());
        verify(storage).deleteObjects(List.of(job.publishedKey("viewer"),job.publishedKey("feed"),job.publishedKey("thumbnail")));
    }
    @Test void givenInvalidMetadata_whenPromoting_thenRejectsBeforeCopy() {
        for(var meta:java.util.List.of(new S3StoragePort.ObjectMetadata(0,"video/mp4","e"),new S3StoragePort.ObjectMetadata(100,"text/html","e"),new S3StoragePort.ObjectMetadata(20_000_000,"video/mp4","e"))) {
            var storage=mock(S3StoragePort.class);
            var job=VideoUploadSession.create(UUID.randomUUID(),1L,2L,3L,LocalDateTime.now(),LocalDateTime.now(),null);
            when(storage.head(anyString())).thenReturn(meta);
            assertThatThrownBy(()->new VideoUploadAssetVerifier(storage).promote(job)).hasMessageContaining("확인");
            verify(storage,never()).promote(anyString(),anyString(),anyString());
        }
    }

    @Test void givenLaterVariantMissing_whenPromoting_thenValidatesEverythingBeforeCopy() {
        var storage=mock(S3StoragePort.class);
        var job=VideoUploadSession.create(UUID.randomUUID(),1L,2L,3L,LocalDateTime.now(),LocalDateTime.now(),null);
        when(storage.head(job.key("viewer"))).thenReturn(new S3StoragePort.ObjectMetadata(100,"video/mp4","viewer-etag"));
        when(storage.head(job.key("feed"))).thenReturn(new S3StoragePort.ObjectMetadata(100,"video/mp4","feed-etag"));
        when(storage.head(job.key("thumbnail"))).thenReturn(null);

        assertThatThrownBy(()->new VideoUploadAssetVerifier(storage).promote(job)).hasMessageContaining("확인");

        verify(storage,never()).promote(anyString(),anyString(),anyString());
    }
}
