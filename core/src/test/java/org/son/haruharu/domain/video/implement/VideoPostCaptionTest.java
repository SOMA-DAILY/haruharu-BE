package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.video.implement.exception.InvalidVideoCaptionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoPostCaptionTest {
    private VideoPost post() {
        return VideoPost.create(1L, LocalDateTime.of(2026, 10, 4, 14, 0), 3L, "원문",
                "video", "thumb", LocalDateTime.of(2026, 10, 4, 14, 1));
    }

    @Test
    @DisplayName("100자 캡션으로 변경해도 영상과 게시 정보는 유지된다")
    void givenHundredCharacters_whenUpdate_thenPreservesOtherFields() {
        VideoPost original = post().withFeedVideoUrl("feed-video");
        VideoPost changed = original.withCaption("가".repeat(100));
        assertThat(changed.caption()).hasSize(100);
        assertThat(changed).usingRecursiveComparison().ignoringFields("caption").isEqualTo(original);
    }

    @Test
    @DisplayName("빈 문구는 캡션을 제거하고 문구 양끝의 공백을 정리한다")
    void givenBlankOrPaddedCaption_whenUpdate_thenNormalizes() {
        assertThat(post().withCaption("  ").caption()).isNull();
        assertThat(post().withCaption(null).caption()).isNull();
        assertThat(post().withCaption("  새 문구  ").caption()).isEqualTo("새 문구");
    }

    @Test
    @DisplayName("업로드와 수정 모두 101자를 거절한다")
    void givenTooLongCaption_whenCreateOrUpdate_thenRejects() {
        assertThatThrownBy(() -> post().withCaption("가".repeat(101))).isInstanceOf(InvalidVideoCaptionException.class);
        assertThatThrownBy(() -> VideoPost.create(1L, LocalDateTime.now(), null, "가".repeat(101),
                "video", null, LocalDateTime.now())).isInstanceOf(InvalidVideoCaptionException.class);
    }
}
