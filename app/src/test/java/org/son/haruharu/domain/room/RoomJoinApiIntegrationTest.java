package org.son.haruharu.domain.room;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotUsableException;
import org.son.haruharu.domain.room.implement.RoomInviteStatus;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.exception.InvalidRoomInviteMaxUsesException;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomEntryPath;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.room.storage.RoomInviteJpaRepository;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.profile.storage.UserProfileJpaRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Autowired RoomMemberWriter roomMemberWriter;
    @Autowired RoomInviteJpaRepository roomInviteJpaRepository;
    @Autowired UserProfileJpaRepository userProfileJpaRepository;

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
        roomInviteService.issue(roomId, "JNK234", 6, null, "admin");

        mockMvc.perform(post("/api/v1/rooms/join")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"JNK234\"}"))
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
                        .content("{\"code\":\"NPE234\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITE_CODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 현재 방이 있어도 공백·소문자 코드를 정규화해 두 번째 방에 입장한다")
    void givenAnotherCurrentRoomAndNormalizedCode_whenJoin_thenRetainsBothMemberships() throws Exception {
        String token = activeUserToken();
        Long firstRoom = createJoinableRoom();
        Long secondRoom = createJoinableRoom();
        roomInviteService.issue(firstRoom, "RMA234", 6, null, "admin");
        roomInviteService.issue(secondRoom, "RMB234", 6, null, "admin");

        mockMvc.perform(post("/api/v1/rooms/join")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"RMA234\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/rooms/join")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"  rmb234  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.room_id").value(secondRoom.intValue()));

        assertThat(roomMemberReader.findByUserId(userIdFrom(token))).extracting(RoomMember::roomId)
                .containsExactlyInAnyOrder(firstRoom, secondRoom);
    }

    @Test
    @DisplayName("같은 방 재입장은 200이며 membership과 usedCount를 중복 생성하지 않는다")
    void givenSameRoomMembership_whenJoinAgain_thenReturnsWithoutDuplicateOrUsageChange() throws Exception {
        String token = activeUserToken();
        Long roomId = createJoinableRoom();
        roomInviteService.issue(roomId, "SAME24", 6, null, "admin");

        join(token, "SAME24");
        int afterFirst = roomInviteJpaRepository.findByCode("SAME24").orElseThrow().toDomain().usedCount();
        join(token, " same24 ");

        assertThat(roomMemberReader.getByRoomId(roomId)).hasSize(1);
        assertThat(roomInviteJpaRepository.findByCode("SAME24").orElseThrow().toDomain().usedCount())
                .isEqualTo(afterFirst).isEqualTo(1);
    }

    @Test
    @DisplayName("재발급 코드의 maxUses=8은 기존 6명 active member가 있는 방에도 즉시 적용된다")
    void givenFullRoomFromPriorInviteUsage_whenJoinWithLargerReissuedCode_thenJoins() throws Exception {
        String token = activeUserToken();
        Long roomId = createJoinableRoom();
        roomInviteService.issue(roomId, "NLD234", 6, null, "admin");
        LocalDateTime joinedAt = LocalDateTime.now().minusMinutes(1);
        for (long userId = 100L; userId < 106L; userId++) {
            roomMemberWriter.save(RoomMember.of(roomId, userId, null, null, joinedAt));
        }
        roomInviteService.reissueFor(roomId, 8, null, "admin");
        String newCode = roomInviteJpaRepository.findByRoomIdOrderByIdDesc(roomId).getFirst().toDomain().code();

        mockMvc.perform(post("/api/v1/rooms/join")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + newCode + "\"}"))
                .andExpect(status().isOk());

        assertThat(roomMemberReader.getByRoomId(roomId)).hasSize(7);
        assertThat(roomInviteJpaRepository.findByCode(newCode).orElseThrow().toDomain().usedCount()).isOne();
    }

    @Test
    @DisplayName("앱 신청자 3명이 먼저 배정된 방에도 초대코드 정원 3명이 모두 들어오고, 4번째 초대 입장은 거부된다(#513)")
    void givenApplicationMembers_whenInviteJoins_thenOnlyInviteMembersCountTowardMaxUses() {
        Long roomId = createJoinableRoom();
        roomInviteService.issue(roomId, "MXK234", 3, null, "admin");
        LocalDateTime joinedAt = LocalDateTime.now().minusMinutes(1);
        for (long i = 0; i < 3; i++) {
            roomMemberWriter.save(RoomMember.fromApplication(roomId, 400L + i, "MALE", null, 900L + i, joinedAt));
        }

        for (long userId = 500L; userId < 503L; userId++) {
            roomInviteService.join(userId, "MXK234");
        }

        assertThatThrownBy(() -> roomInviteService.join(503L, "MXK234"))
                .isInstanceOf(InviteCodeNotUsableException.class);
        List<RoomMember> members = roomMemberReader.getByRoomId(roomId);
        assertThat(members).hasSize(6);
        assertThat(members).filteredOn(member -> member.entryPath() == RoomEntryPath.INVITE)
                .extracting(RoomMember::userId).containsExactlyInAnyOrder(500L, 501L, 502L);
        assertThat(members).filteredOn(member -> member.entryPath() == RoomEntryPath.APPLICATION)
                .extracting(RoomMember::applicationId).containsExactlyInAnyOrder(900L, 901L, 902L);
    }

    @Test
    @DisplayName("maxUses가 null이면 active member가 여섯 명을 넘어도 무제한으로 입장한다")
    void givenUnlimitedInvite_whenSevenUsersJoin_thenAllMembershipsAreCreated() {
        Long roomId = createJoinableRoom();
        roomInviteService.issue(roomId, "UNL234", null, null, "admin");

        for (long userId = 200L; userId < 207L; userId++) {
            roomInviteService.join(userId, "UNL234");
        }

        assertThat(roomMemberReader.getByRoomId(roomId)).hasSize(7);
        assertThat(roomInviteJpaRepository.findByCode("UNL234").orElseThrow().toDomain().usedCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("maxUses를 2에서 1로 재발급하면 기존·새 코드 usage는 보존되고 새 멤버는 즉시 거부된다")
    void givenReissuedSmallerCapacity_whenNewUserJoins_thenRejectsWithoutChangingPerCodeUsage() {
        Long roomId = createJoinableRoom();
        roomInviteService.issue(roomId, "SHRNK2", 2, null, "admin");
        roomInviteService.join(300L, "SHRNK2");
        roomInviteService.reissueFor(roomId, 1, null, "admin");
        String newCode = roomInviteJpaRepository.findByRoomIdOrderByIdDesc(roomId).getFirst().toDomain().code();

        assertThatThrownBy(() -> roomInviteService.join(301L, newCode))
                .isInstanceOf(InviteCodeNotUsableException.class);

        List<RoomInvite> invites = roomInviteJpaRepository
                .findByRoomIdOrderByIdDesc(roomId).stream().map(entity -> entity.toDomain()).toList();
        assertThat(invites).extracting(RoomInvite::maxUses)
                .containsExactly(1, 2);
        assertThat(invites).extracting(RoomInvite::usedCount)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("core 재발급이 non-positive maxUses로 실패하면 기존 ACTIVE 코드의 상태·행 수·usage가 롤백된다")
    void givenInvalidDirectReissue_whenFactoryRejects_thenTransactionPreservesExistingActiveInvite() {
        Long roomId = createJoinableRoom();
        roomInviteService.issue(roomId, "RQLL24", 2, null, "admin");
        roomInviteService.join(400L, "RQLL24");

        assertThatThrownBy(() -> roomInviteService.reissueFor(roomId, 0, null, "admin"))
                .isInstanceOf(InvalidRoomInviteMaxUsesException.class);

        List<RoomInvite> invites = roomInviteJpaRepository.findByRoomIdOrderByIdDesc(roomId).stream()
                .map(entity -> entity.toDomain()).toList();
        assertThat(invites).hasSize(1);
        assertThat(invites.getFirst().status()).isEqualTo(RoomInviteStatus.ACTIVE);
        assertThat(invites.getFirst().usedCount()).isOne();
        assertThat(invites.getFirst().code()).isEqualTo("RQLL24");
    }

    @Test
    @DisplayName("잘못된 코드 형식은 HTTP 400이며 membership과 usage를 바꾸지 않는다")
    void givenInvalidFormat_whenJoin_thenBadRequestWithoutSideEffects() throws Exception {
        String token = activeUserToken();

        mockMvc.perform(post("/api/v1/rooms/join")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"I01234\"}"))
                .andExpect(status().isBadRequest());

        assertThat(roomMemberReader.findByUserId(userIdFrom(token))).isEmpty();
    }

    @Test
    @DisplayName("여러 현재 방이 있으면 ACTIVE·SELECTING·PENDING과 낮은 roomId 순으로 대표 방을 반환한다")
    void givenSeveralCurrentRooms_whenGetMyRoom_thenReturnsStatusPriorityRepresentative() throws Exception {
        String token = activeUserToken();
        Long userId = userIdFrom(token);
        Room pending = roomWriter.save(new Room(null, RoomStatus.PENDING, LocalDate.now(), 3, null, null));
        Room active = roomWriter.save(new Room(null, RoomStatus.ACTIVE, LocalDate.now(), 3,
                LocalDateTime.now().minusHours(1), null));
        Room selecting = roomWriter.save(new Room(null, RoomStatus.SELECTING, LocalDate.now(), 3,
                LocalDateTime.now().minusHours(1), null));
        List.of(pending, active, selecting).forEach(room -> roomMemberWriter.save(
                RoomMember.of(room.id(), userId, null, null, LocalDateTime.now())));

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/rooms/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.room_id").value(active.id().intValue()))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }
    }

    @Test
    @DisplayName("성별 프로필 유저들이 순서대로 입장하면 room_members.pseudonym이 성별별 입장 순번대로 배정된다(#393)")
    void givenGenderedProfiles_whenJoinInOrder_thenAssignsPseudonymByJoinOrder() throws Exception {
        Long roomId = createJoinableRoom();
        roomInviteService.issue(roomId, "PSN234", 6, null, "admin");

        join(genderedUserToken(Gender.MALE), "PSN234");   // 남성 1번째 → 민준
        join(genderedUserToken(Gender.MALE), "PSN234");   // 남성 2번째 → 정석
        join(genderedUserToken(Gender.FEMALE), "PSN234"); // 여성 1번째 → 유진

        assertThat(roomMemberReader.getByRoomId(roomId))
                .extracting(RoomMember::pseudonym)
                .containsExactlyInAnyOrder("민준", "정석", "유진");
    }

    private String genderedUserToken(Gender gender) {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        userProfileJpaRepository.save(UserProfileEntity.from(
                new UserProfile(null, userId, null, null, null, null, "닉" + userId, null, null, gender)));
        return jwtProvider.generateAccessToken(userId);
    }

    private void join(String token, String code) throws Exception {
        mockMvc.perform(post("/api/v1/rooms/join")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
    }

    private Long userIdFrom(String token) {
        return jwtProvider.extractUserId(token);
    }
}
