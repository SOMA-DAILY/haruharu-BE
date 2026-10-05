package org.son.haruharu.domain.video.implement;

import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.video.implement.exception.InvalidVideoUploadAssetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoUploadVariantTest {

    @Test
    void givenApiValue_whenConverting_thenMatchesVariant() {
        assertThat(VideoUploadVariant.from("viewer")).isEqualTo(VideoUploadVariant.VIEWER);
        assertThat(VideoUploadVariant.from("feed")).isEqualTo(VideoUploadVariant.FEED);
        assertThat(VideoUploadVariant.from("thumbnail")).isEqualTo(VideoUploadVariant.THUMBNAIL);
    }

    @Test
    void givenUnknownValue_whenConverting_thenRejectsAsInvalidAsset() {
        assertThatThrownBy(() -> VideoUploadVariant.from("original"))
                .isInstanceOf(InvalidVideoUploadAssetException.class);
        assertThatThrownBy(() -> VideoUploadVariant.from("VIEWER"))
                .isInstanceOf(InvalidVideoUploadAssetException.class);
    }

    @Test
    void givenQuickTimeCameraOriginal_whenResolvingContentType_thenAcceptsOnlyViewer() {
        assertThat(VideoUploadVariant.VIEWER.resolveContentType(null)).isEqualTo("video/mp4");
        assertThat(VideoUploadVariant.VIEWER.resolveContentType("video/quicktime")).isEqualTo("video/quicktime");
        assertThat(VideoUploadVariant.THUMBNAIL.resolveContentType(null)).isEqualTo("image/jpeg");
        assertThatThrownBy(() -> VideoUploadVariant.FEED.resolveContentType("video/quicktime"))
                .isInstanceOf(InvalidVideoUploadAssetException.class);
        assertThatThrownBy(() -> VideoUploadVariant.VIEWER.resolveContentType("video/webm"))
                .isInstanceOf(InvalidVideoUploadAssetException.class);
    }

    @Test
    void givenObjectSize_whenCheckingLimit_thenAllowsOnlyNonEmptyWithinMax() {
        assertThat(VideoUploadVariant.FEED.allowsSize(0)).isFalse();
        assertThat(VideoUploadVariant.FEED.allowsSize(5_000_000)).isTrue();
        assertThat(VideoUploadVariant.FEED.allowsSize(5_000_001)).isFalse();
        assertThat(VideoUploadVariant.VIEWER.allowsSize(10_000_000)).isTrue();
    }
}
