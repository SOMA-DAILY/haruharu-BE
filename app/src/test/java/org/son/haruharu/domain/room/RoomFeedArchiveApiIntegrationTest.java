package org.son.haruharu.domain.room;

import java.time.LocalDateTime;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.son.haruharu.domain.video.storage.VideoPostJpaRepository;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 피드 과거 회고 페이지네이션 API end-to-end(#350). before 파라미터(ISO LocalDateTime) 바인딩·엔드포인트 배선·
 * 공개 영상 슬롯 필터와 과거 cursor를 실 HTTP로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomFeedArchiveApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomMemberWriter roomMemberWriter;
    @Autowired VideoPostJpaRepository videoPostJpaRepository;

    @MockitoBean S3StoragePort s3StoragePort;

    // 정시로 맞춘 6시간 전 시작(진행 중). 과거 페이지는 startsAt 상대라 시계와 무관하게 결정론적이다.
    private final LocalDateTime startsAt = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).minusHours(6);

    private Long roomId;
    private String token;

    @BeforeEach
    void setUp() {
        videoPostJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        token = jwtProvider.generateAccessToken(userId);
        Room room = roomWriter.save(Room.createScheduled(startsAt,
                startsAt.plusDays(2), startsAt.plusDays(2).plusHours(2),
                startsAt.plusDays(2).plusHours(3), startsAt.plusDays(3), 3));
        roomId = room.id();
        roomMemberWriter.save(RoomMember.of(roomId, userId, "MALE", null, startsAt));
    }

    @Test
    @DisplayName("빈 현재 피드는 현재 촬영 슬롯만 locked=false로 반환한다")
    void givenNoUpload_whenGetFeed_thenKeepsOnlyCurrentSlot() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{id}/feed", roomId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots.length()").value(1))
                .andExpect(jsonPath("$.data.slots[0].current").value(true))
                .andExpect(jsonPath("$.data.slots[0].locked").value(false))
                .andExpect(jsonPath("$.data.posts").isEmpty());
    }

    @Test
    @DisplayName("빈 historical page는 raw cursor를 진행하고 방 시작에서 null로 끝난다")
    void givenEmptyHistoricalPages_whenGetFeed_thenAdvancesCursorUntilStart() throws Exception {
        String firstCursor = mockMvc.perform(get("/api/v1/rooms/{id}/feed", roomId)
                        .param("before", startsAt.plusHours(6).toString()).param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots").isEmpty())
                .andExpect(jsonPath("$.data.posts").isEmpty())
                .andExpect(jsonPath("$.data.next_cursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        firstCursor = JsonPath.read(firstCursor, "$.data.next_cursor");

        String secondCursor = mockMvc.perform(get("/api/v1/rooms/{id}/feed", roomId)
                        .param("before", firstCursor).param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots").isEmpty())
                .andExpect(jsonPath("$.data.posts").isEmpty())
                .andExpect(jsonPath("$.data.next_cursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        secondCursor = JsonPath.read(secondCursor, "$.data.next_cursor");

        mockMvc.perform(get("/api/v1/rooms/{id}/feed", roomId)
                        .param("before", secondCursor).param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots").isEmpty())
                .andExpect(jsonPath("$.data.posts").isEmpty())
                .andExpect(jsonPath("$.data.next_cursor").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("요청자가 업로드하지 않아도 타 멤버 공개 영상이 피드에 반환된다")
    void givenOtherMemberPost_whenGetFeed_thenReturnsPost() throws Exception {
        Long otherUserId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null)))
                .toDomain().id();
        RoomMember other = roomMemberWriter.save(RoomMember.of(roomId, otherUserId, "FEMALE", null, startsAt));
        LocalDateTime postSlot = startsAt.plusHours(5);
        videoPostJpaRepository.save(VideoPostEntity.from(
                VideoPost.create(other.id(), postSlot, null, "hello", "video-key", "thumb-key", startsAt.plusHours(5))));
        given(s3StoragePort.createPresignedGetUrl(anyString())).willAnswer(invocation ->
                "signed:" + invocation.getArgument(0, String.class));

        mockMvc.perform(get("/api/v1/rooms/{id}/feed", roomId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots[*].locked").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(false))))
                .andExpect(jsonPath("$.data.posts.length()").value(1))
                .andExpect(jsonPath("$.data.posts[0].video_url").value("signed:video-key"));
    }
}
