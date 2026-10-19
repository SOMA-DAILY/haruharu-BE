package org.son.haruharu.domain.video;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.son.haruharu.domain.video.application.dto.VideoUploadRequest;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostRepository;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.son.haruharu.domain.video.storage.VideoPostJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VideoCaptionApiIntegrationTest {
    private static final Long OWNER = 9100001L;
    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 10, 10, 14, 0);

    private final MockMvc mockMvc;
    private final VideoPostJpaRepository posts;
    private final RoomMemberJpaRepository members;
    private final Validator validator;
    private final VideoPostRepository repository;
    private VideoPost original;

    @Autowired
    VideoCaptionApiIntegrationTest(MockMvc mockMvc, VideoPostJpaRepository posts,
                                   RoomMemberJpaRepository members, Validator validator,
                                   VideoPostRepository repository) {
        this.mockMvc = mockMvc;
        this.posts = posts;
        this.members = members;
        this.validator = validator;
        this.repository = repository;
    }

    @BeforeEach
    void setUp() {
        Long memberId = members.save(RoomMemberEntity.from(
                RoomMember.of(498L, OWNER, "MALE", null, SLOT))).toDomain().id();
        original = posts.saveAndFlush(VideoPostEntity.from(VideoPost.create(
                memberId, SLOT, null, "원래 캡션", "https://example.test/video.mp4",
                "https://example.test/thumb.jpg", SLOT.plusMinutes(1)))).toDomain();
    }

    @Test
    @DisplayName("100자 캡션을 수정하면 원본 영상과 슬롯 및 게시 시각을 유지한다")
    void givenOwnVideo_whenUpdateHundredCharacters_thenOnlyCaptionChanges() throws Exception {
        String caption = "가".repeat(100);
        update(OWNER, original.id(), caption)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.video_post_id").value(original.id()))
                .andExpect(jsonPath("$.data.caption").value(caption));
        VideoPost updated = posts.findById(original.id()).orElseThrow().toDomain();
        assertThat(updated).usingRecursiveComparison().ignoringFields("caption").isEqualTo(original);
        assertThat(updated.caption()).isEqualTo(caption);
    }

    @Test
    @DisplayName("빈 문구를 저장하면 영상은 유지하고 캡션을 제거한다")
    void givenOwnCaption_whenSaveWhitespace_thenRemovesCaption() throws Exception {
        update(OWNER, original.id(), "   ").andExpect(status().isOk());
        VideoPost updated = posts.findById(original.id()).orElseThrow().toDomain();
        assertThat(updated.caption()).isNull();
        assertThat(updated.videoUrl()).isEqualTo(original.videoUrl());
    }

    @Test
    @DisplayName("101자 수정 요청은 거절하고 기존 문구를 유지한다")
    void givenTooLongCaption_whenUpdate_thenRejectsWithoutChanges() throws Exception {
        update(OWNER, original.id(), "가".repeat(101)).andExpect(status().isBadRequest());
        assertThat(posts.findById(original.id()).orElseThrow().toDomain()).isEqualTo(original);
    }

    @Test
    @DisplayName("타인의 캡션은 수정할 수 없다")
    void givenOtherUser_whenUpdate_thenForbidden() throws Exception {
        update(9100004L, original.id(), "수정 시도").andExpect(status().isForbidden());
        assertThat(posts.findById(original.id()).orElseThrow().toDomain()).isEqualTo(original);
    }

    @Test
    @DisplayName("삭제된 영상의 캡션은 수정할 수 없다")
    void givenDeletedVideo_whenUpdate_thenNotFound() throws Exception {
        posts.saveAndFlush(VideoPostEntity.from(original.deletedAt(SLOT.plusMinutes(2))));
        update(OWNER, original.id(), "수정 시도").andExpect(status().isNotFound());
        assertThat(posts.findById(original.id()).orElseThrow().toDomain().deletedAt()).isNotNull();
    }

    @Test
    @DisplayName("숨김 영상의 캡션은 수정할 수 없다")
    void givenHiddenVideo_whenUpdate_thenNotFound() throws Exception {
        VideoPost hidden = new VideoPost(original.id(), original.roomMemberId(), original.slotStart(), original.missionId(),
                original.caption(), original.videoUrl(), original.thumbnailUrl(), true, "신고 검토", null, original.createdAt());
        posts.saveAndFlush(VideoPostEntity.from(hidden));
        update(OWNER, original.id(), "수정 시도").andExpect(status().isNotFound());
        assertThat(posts.findById(original.id()).orElseThrow().toDomain().isHidden()).isTrue();
    }

    @Test
    @DisplayName("수정 중 영상이 삭제되면 캡션 쓰기는 삭제 상태를 되돌리지 않는다")
    void givenVideoDeletedAfterRead_whenWriteCaption_thenDoesNotRestoreVideo() {
        VideoPost deleted = original.deletedAt(SLOT.plusMinutes(2));
        posts.saveAndFlush(VideoPostEntity.from(deleted));
        assertThat(repository.updateCaption(original.id(), "늦게 도착한 수정")).isFalse();
        assertThat(posts.findById(original.id()).orElseThrow().toDomain()).isEqualTo(deleted);
    }

    @Test
    @DisplayName("존재하지 않는 영상의 캡션 수정은 404다")
    void givenMissingVideo_whenUpdate_thenNotFound() throws Exception {
        update(OWNER, Long.MAX_VALUE, "수정 시도").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("caption 필드가 없는 요청은 캡션을 제거하지 않고 거절한다")
    void givenMissingCaptionField_whenUpdate_thenBadRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/video-posts/{id}/caption", original.id())
                        .with(authentication(auth(OWNER))).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(posts.findById(original.id()).orElseThrow().toDomain().caption()).isEqualTo(original.caption());
    }

    @Test
    @DisplayName("업로드 요청은 100자를 허용하고 101자를 거절한다")
    void givenUploadCaption_whenValidate_thenUsesHundredCharacterLimit() {
        assertThat(validator.validate(new VideoUploadRequest("video", null, "가".repeat(100)))).isEmpty();
        assertThat(validator.validate(new VideoUploadRequest("video", null, "가".repeat(101)))).isNotEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions update(Long userId, Long postId, String caption)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/video-posts/{id}/caption", postId)
                .with(authentication(auth(userId))).contentType(APPLICATION_JSON)
                .content("{\"caption\":\"" + caption + "\"}"));
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
