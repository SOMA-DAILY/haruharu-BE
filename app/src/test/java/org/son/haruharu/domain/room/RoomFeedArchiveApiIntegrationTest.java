package org.son.haruharu.domain.room;

import java.time.LocalDateTime;

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
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 피드 과거 회고 페이지네이션 API end-to-end(#350). before 파라미터(ISO LocalDateTime) 바인딩·엔드포인트 배선·
 * 게이트("안 올린 슬롯은 잠김")를 실 HTTP로 검증한다. 업로드가 없어 presigned URL 경로(S3)는 타지 않는다.
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

    // 정시로 맞춘 6시간 전 시작(진행 중). 과거 페이지는 startsAt 상대라 시계와 무관하게 결정론적이다.
    private final LocalDateTime startsAt = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).minusHours(6);

    private Long roomId;
    private String token;

    @BeforeEach
    void setUp() {
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
    @DisplayName("초기 피드는 노출 창 슬롯을 잠금(미업로드) 상태로 반환하고 posts는 비어 있다")
    void givenNoUpload_whenGetFeed_thenLockedSlotsNoPosts() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/{id}/feed", roomId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots").isNotEmpty())
                .andExpect(jsonPath("$.data.slots[*].locked").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(true))))
                .andExpect(jsonPath("$.data.posts").isEmpty());
    }

    @Test
    @DisplayName("before 커서로 과거를 방 시작까지 조회하면 그 슬롯들이 잠긴 채 반환되고 next_cursor는 null이다")
    void givenBeforeCursorToStart_whenGetFeed_thenPastSlotsAndNullCursor() throws Exception {
        // before = startsAt+3h → 과거 슬롯 [startsAt, +1h, +2h] (방 시작에 닿아 더 과거 없음)
        String before = startsAt.plusHours(3).toString();

        mockMvc.perform(get("/api/v1/rooms/{id}/feed", roomId)
                        .param("before", before).param("size", "6")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots.length()").value(3))
                .andExpect(jsonPath("$.data.slots[*].locked").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(true))))
                .andExpect(jsonPath("$.data.slots[*].current").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(false))))
                .andExpect(jsonPath("$.data.posts").isEmpty())
                .andExpect(jsonPath("$.data.next_cursor").value(org.hamcrest.Matchers.nullValue()));
    }
}
