package org.son.haruharu.domain.video;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostRepository;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.son.haruharu.domain.video.storage.VideoPostJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VideoPostVisibleSlotRepositoryIntegrationTest {

    private static final LocalDateTime SLOT_20 = LocalDateTime.of(2026, 6, 26, 20, 0);
    private static final LocalDateTime SLOT_21 = SLOT_20.plusHours(1);
    private static final LocalDateTime SLOT_22 = SLOT_20.plusHours(2);

    @Autowired VideoPostJpaRepository jpaRepository;
    @Autowired VideoPostRepository videoPostRepository;

    @BeforeEach
    void clean() {
        jpaRepository.deleteAll();
    }

    @Test
    @DisplayName("visible 슬롯 조회는 타 멤버 공개 영상을 포함하고 숨김·삭제 영상을 제외하며 중복 슬롯은 한 번만 반환한다")
    void givenMixedPosts_whenFindVisibleSlotStarts_thenReturnsDistinctVisibleSlots() {
        save(VideoPost.create(200L, SLOT_20, null, null, "visible-a", null, SLOT_20));
        save(VideoPost.create(201L, SLOT_20, null, null, "visible-b", null, SLOT_20));
        save(new VideoPost(null, 202L, SLOT_21, null, null, "hidden", null,
                true, "moderation", null, SLOT_21));
        save(VideoPost.create(203L, SLOT_22, null, null, "deleted", null, SLOT_22)
                .deletedAt(SLOT_22.plusMinutes(1)));

        List<LocalDateTime> result = videoPostRepository.findVisibleSlotStarts(
                List.of(200L, 201L, 202L, 203L), List.of(SLOT_20, SLOT_21, SLOT_22));

        assertThat(result).containsExactly(SLOT_20);
    }

    private void save(VideoPost post) {
        jpaRepository.save(VideoPostEntity.from(post));
    }
}
