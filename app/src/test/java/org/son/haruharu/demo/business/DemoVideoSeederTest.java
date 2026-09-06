package org.son.haruharu.demo.business;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoVideoSeederTest {

    private static final String VIDEO_URL = "https://bucket.s3/demo/placeholder.mp4";
    private static final String THUMB_URL = "https://picsum.photos/x";

    @Mock VideoPostWriter videoPostWriter;

    private RoomMember member(Long id) {
        return new RoomMember(id, 10L, id, "MALE", null, null, LocalDateTime.now());
    }

    @Test
    @DisplayName("과거 슬롯에 전 멤버의 영상을 심고 현재 슬롯은 비운다")
    void givenPastSlots_whenSeed_thenSeedsAllMembersExceptCurrent() {
        DemoVideoSeeder seeder = new DemoVideoSeeder(videoPostWriter, VIDEO_URL, THUMB_URL);
        // startsAt=10:00, now=12:00 → 노출 창 [10,11,12], 현재=12:00 → 과거 슬롯 [10:00, 11:00]
        LocalDateTime startsAt = LocalDateTime.of(2026, 6, 24, 10, 0);
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 12, 0);

        seeder.seed(List.of(member(1L), member(2L)), startsAt, now);

        // 멤버 2 × 과거 슬롯 2 = 4개, 전부 고정 URL
        ArgumentCaptor<VideoPost> captor = ArgumentCaptor.forClass(VideoPost.class);
        verify(videoPostWriter, org.mockito.Mockito.times(4)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(VideoPost::slotStart)
                .containsExactlyInAnyOrder(
                        startsAt, startsAt, startsAt.plusHours(1), startsAt.plusHours(1));
        assertThat(captor.getAllValues()).allMatch(v -> v.videoUrl().equals(VIDEO_URL));
        // 현재 슬롯(12:00)은 심지 않는다
        assertThat(captor.getAllValues()).extracting(VideoPost::slotStart).doesNotContain(now);
    }

    @Test
    @DisplayName("영상 URL 미설정이면 아무것도 심지 않는다(촬영 CTA만)")
    void givenBlankUrl_whenSeed_thenNoOp() {
        DemoVideoSeeder seeder = new DemoVideoSeeder(videoPostWriter, "", THUMB_URL);

        seeder.seed(List.of(member(1L)), LocalDateTime.of(2026, 6, 24, 10, 0),
                LocalDateTime.of(2026, 6, 24, 12, 0));

        verify(videoPostWriter, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
