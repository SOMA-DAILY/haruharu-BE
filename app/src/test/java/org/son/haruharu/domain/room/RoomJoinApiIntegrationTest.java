package org.son.haruharu.domain.room;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 초대코드 입장 API end-to-end(#312). 코드 발급 → POST /rooms/join → room_member 생성까지 실 HTTP·영속성으로 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomJoinApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomInviteService roomInviteService;
    @Autowired RoomMemberReader roomMemberReader;

    @BeforeEach
    void clean() {
        userJpaRepository.deleteAll();
    }

    private String activeUserToken() {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        return jwtProvider.generateAccessToken(userId);
    }

    private Long createJoinableRoom() {
        LocalDateTime starts = LocalDateTime.now().plusHours(1); // now < startsAt → PENDING(입장 가능)
        Room room = roomWriter.save(Room.createScheduled(starts, starts.plusDays(2).withHour(20),
                starts.plusDays(2).withHour(22), starts.plusDays(2).withHour(23),
                starts.plusDays(2).withHour(23).withMinute(30), 3));
        return room.id();
    }

    @Test
    @DisplayName("유효한 초대코드로 입장하면 200과 roomId를 받고 room_member가 생성된다")
    void givenValidCode_whenJoin_thenOkAndMemberCreated() throws Exception {
        String token = activeUserToken();
        Long roomId = createJoinableRoom();
        roomInviteService.issue(roomId, "JOINOK", 6, null, "admin");

        mockMvc.perform(post("/api/v1/rooms/join")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"JOINOK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        assertThat(roomMemberReader.getByRoomId(roomId)).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 코드로 입장하면 404 INVITE_CODE_NOT_FOUND")
    void givenUnknownCode_whenJoin_thenNotFound() throws Exception {
        String token = activeUserToken();

        mockMvc.perform(post("/api/v1/rooms/join")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOPE12\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITE_CODE_NOT_FOUND"));
    }
}
