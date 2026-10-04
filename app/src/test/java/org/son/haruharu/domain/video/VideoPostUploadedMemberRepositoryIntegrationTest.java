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
class VideoPostUploadedMemberRepositoryIntegrationTest {

    // 이번 슬롯 23:00 기준 직전 2개 슬롯은 21:00·22:00, 20:00 은 2시간 경계 밖.
    private static final LocalDateTime SLOT_20 = LocalDateTime.of(2026, 6, 26, 20, 0);
    private static final LocalDateTime SLOT_21 = SLOT_20.plusHours(1);
    private static final LocalDateTime SLOT_22 = SLOT_20.plusHours(2);
    private static final List<LocalDateTime> RECENT_TWO_SLOTS = List.of(SLOT_21, SLOT_22);

    @Autowired VideoPostJpaRepository jpaRepository;
    @Autowired VideoPostRepository videoPostRepository;

    @BeforeEach
    void clean() {
        jpaRepository.deleteAll();
    }

    @Test
    @DisplayName("주어진 슬롯에 활성 영상을 올린 멤버 id를 중복 없이 반환한다 — 숨김은 업로드로 치고 삭제는 제외한다")
    void givenMixedPosts_whenFindUploadedRoomMemberIds_thenReturnsDistinctUploaders() {
        save(VideoPost.create(200L, SLOT_22, null, null, "last-slot", null, SLOT_22));
        save(VideoPost.create(201L, SLOT_21, null, null, "two-slots-ago", null, SLOT_21));
        save(VideoPost.create(202L, SLOT_20, null, null, "out-of-range", null, SLOT_20));
        save(VideoPost.create(203L, SLOT_22, null, null, "deleted", null, SLOT_22)
                .deletedAt(SLOT_22.plusMinutes(1)));
        save(new VideoPost(null, 204L, SLOT_21, null, null, "hidden", null,
                true, "moderation", null, SLOT_21));
        save(VideoPost.create(205L, SLOT_21, null, null, "both-a", null, SLOT_21));
        save(VideoPost.create(205L, SLOT_22, null, null, "both-b", null, SLOT_22));
        save(VideoPost.create(299L, SLOT_22, null, null, "other-room", null, SLOT_22));

        List<Long> result = videoPostRepository.findUploadedRoomMemberIds(
                List.of(200L, 201L, 202L, 203L, 204L, 205L), RECENT_TWO_SLOTS);

        assertThat(result).containsExactlyInAnyOrder(200L, 201L, 204L, 205L);
    }

    private void save(VideoPost post) {
        jpaRepository.save(VideoPostEntity.from(post));
    }
}
