package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MatchResultResponse;
import org.son.haruharu.domain.room.business.dto.PendingMatchResultResponse;
import org.son.haruharu.domain.room.business.dto.PendingMatchResultResponse.Outcome;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.storage.exception.MatchResultNotFoundException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MatchResultServiceTest {

    @Mock RoomMemberReader roomMemberReader;
    @Mock MatchResultReader matchResultReader;
    @Mock ChatRoomReader chatRoomReader;
    @Mock UserProfileReader userProfileReader;
    @Mock UserImageReader userImageReader;
    @Mock S3StoragePort s3StoragePort;
    @Mock RoomReader roomReader;

    MatchResultService service;

    private final Long roomId = 10L;
    private final Long userId = 1L;
    private final Long meId = 20L;
    private final Long partnerId = 30L;
    private final Long partnerUserId = 2L;
    private final Long matchResultId = 40L;

    // 기본 고정 시각: 2026-07-12 23:00 KST — 아래 activeRoom()(Day3=07-12)의 발표(22:00) 이후
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-12T14:00:00Z"), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        service = serviceAt(clock);
    }

    private MatchResultService serviceAt(Clock fixedClock) {
        return new MatchResultService(roomMemberReader, matchResultReader, chatRoomReader,
                userProfileReader, userImageReader, s3StoragePort, roomReader, fixedClock);
    }

    /** started 2026-07-10 09:00, totalDays 3 → Day3=2026-07-12, 발표 시각 2026-07-12 22:00 */
    private Room activeRoom() {
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 10), 3,
                LocalDateTime.of(2026, 7, 10, 9, 0), null);
    }

    private RoomMember me() {
        return new RoomMember(meId, roomId, userId, "MALE", null, LocalDateTime.now());
    }

    private RoomMember partner() {
        return new RoomMember(partnerId, roomId, partnerUserId, "FEMALE", null, LocalDateTime.now());
    }

    private UserProfile profileOf(String nickname) {
        return new UserProfile(5L, partnerUserId, "서울", "직장인", 168, "INFP", nickname, null, null);
    }

    @Test
    @DisplayName("매칭이 있으면 상대 멤버 id와 함께 chatRoomId·상대 프로필 요약(닉네임·대표사진)을 반환한다")
    void givenMatched_whenGet_thenReturnsPartnerWithChatRoomAndProfile() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me());
        // me가 memberB인 케이스
        given(matchResultReader.getByMember(meId))
                .willReturn(new MatchResult(matchResultId, partnerId, meId, LocalDateTime.now()));
        given(roomMemberReader.getById(partnerId)).willReturn(partner());
        given(chatRoomReader.findChatRoomIdByMatchResultId(matchResultId)).willReturn(Optional.of(77L));
        given(userProfileReader.findByUserId(partnerUserId)).willReturn(Optional.of(profileOf("하늘")));
        given(userImageReader.getRepresentativeImageUrl(partnerUserId)).willReturn("https://cdn/rep.jpg");
        given(s3StoragePort.createPresignedImageGetUrl("https://cdn/rep.jpg"))
                .willReturn("https://cdn/rep.jpg?sig");

        MatchResultResponse result = service.getMatchResult(roomId, userId);

        assertThat(result.partnerMemberId()).isEqualTo(partnerId);
        assertThat(result.chatRoomId()).isEqualTo(77L);
        assertThat(result.partner().nickname()).isEqualTo("하늘");
        assertThat(result.partner().profileImageUrl()).isEqualTo("https://cdn/rep.jpg?sig");
    }

    @Test
    @DisplayName("상대 대표사진·채팅방이 아직 없으면 해당 필드는 null로 반환한다")
    void givenNoImageOrChatRoom_whenGet_thenNullFields() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me());
        given(matchResultReader.getByMember(meId))
                .willReturn(new MatchResult(matchResultId, meId, partnerId, LocalDateTime.now()));
        given(roomMemberReader.getById(partnerId)).willReturn(partner());
        given(chatRoomReader.findChatRoomIdByMatchResultId(matchResultId)).willReturn(Optional.empty());
        given(userProfileReader.findByUserId(partnerUserId)).willReturn(Optional.of(profileOf("하늘")));
        given(userImageReader.getRepresentativeImageUrl(partnerUserId)).willReturn(null);

        MatchResultResponse result = service.getMatchResult(roomId, userId);

        assertThat(result.chatRoomId()).isNull();
        assertThat(result.partner().profileImageUrl()).isNull();
        assertThat(result.partner().nickname()).isEqualTo("하늘");
    }

    @Test
    @DisplayName("매칭이 없으면 MatchResultNotFoundException(404)이 발생한다")
    void givenNoMatch_whenGet_thenThrows() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me());
        given(matchResultReader.getByMember(meId)).willThrow(new MatchResultNotFoundException());

        assertThatThrownBy(() -> service.getMatchResult(roomId, userId))
                .isInstanceOf(MatchResultNotFoundException.class);
    }

    @Test
    @DisplayName("발표가 지난 최근 방에서 상호매칭이 성사됐으면 MATCHED pending_result가 반환된다")
    void givenAnnouncedAndMatched_whenFindPendingResult_thenMatched() {
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of(me()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeRoom()));
        given(matchResultReader.findByMember(meId))
                .willReturn(Optional.of(new MatchResult(matchResultId, partnerId, meId, LocalDateTime.now())));
        given(roomMemberReader.getById(partnerId)).willReturn(partner());
        given(userProfileReader.findByUserId(partnerUserId)).willReturn(Optional.of(profileOf("하늘")));
        given(chatRoomReader.findChatRoomIdByMatchResultId(matchResultId)).willReturn(Optional.of(77L));

        PendingMatchResultResponse result = service.findPendingResult(userId).orElseThrow();

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.outcome()).isEqualTo(Outcome.MATCHED);
        assertThat(result.chatRoomId()).isEqualTo(77L);
        assertThat(result.partnerNickname()).isEqualTo("하늘");
        assertThat(result.announcedAt()).isEqualTo(LocalDateTime.of(2026, 7, 12, 22, 0));
    }

    @Test
    @DisplayName("MATCHED여도 채팅방 생성 이벤트 처리 전이면 chat_room_id는 null이다")
    void givenMatchedBeforeChatRoomCreated_whenFindPendingResult_thenNullChatRoomId() {
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of(me()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeRoom()));
        given(matchResultReader.findByMember(meId))
                .willReturn(Optional.of(new MatchResult(matchResultId, meId, partnerId, LocalDateTime.now())));
        given(roomMemberReader.getById(partnerId)).willReturn(partner());
        given(userProfileReader.findByUserId(partnerUserId)).willReturn(Optional.of(profileOf("하늘")));
        given(chatRoomReader.findChatRoomIdByMatchResultId(matchResultId)).willReturn(Optional.empty());

        PendingMatchResultResponse result = service.findPendingResult(userId).orElseThrow();

        assertThat(result.outcome()).isEqualTo(Outcome.MATCHED);
        assertThat(result.chatRoomId()).isNull();
    }

    @Test
    @DisplayName("발표가 지났는데 상호매칭이 없으면 FAILED pending_result가 반환된다")
    void givenAnnouncedAndNoMatch_whenFindPendingResult_thenFailed() {
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of(me()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeRoom()));
        given(matchResultReader.findByMember(meId)).willReturn(Optional.empty());

        PendingMatchResultResponse result = service.findPendingResult(userId).orElseThrow();

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.chatRoomId()).isNull();
        assertThat(result.partnerNickname()).isNull();
        assertThat(result.announcedAt()).isEqualTo(LocalDateTime.of(2026, 7, 12, 22, 0));
    }

    @Test
    @DisplayName("발표 시각(Day3 22:00) 전에는 pending_result가 비어 있다")
    void givenBeforeAnnounce_whenFindPendingResult_thenEmpty() {
        // 2026-07-12 21:00 KST — 발표 1시간 전
        MatchResultService before = serviceAt(
                Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneId.of("Asia/Seoul")));
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of(me()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(activeRoom()));

        assertThat(before.findPendingResult(userId)).isEmpty();
    }

    @Test
    @DisplayName("방이 ENDED 처리된 뒤에도 pending_result는 계속 반환된다")
    void givenEndedRoom_whenFindPendingResult_thenStillReturned() {
        // started 07-08 → Day3=07-10 발표 07-10 22:00, 익일 09:00 ENDED. 기본 시각(07-12 23:00)엔 종료 후.
        Room ended = new Room(roomId, RoomStatus.ENDED, LocalDate.of(2026, 7, 8), 3,
                LocalDateTime.of(2026, 7, 8, 9, 0), LocalDateTime.of(2026, 7, 11, 9, 0));
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of(me()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(ended));
        given(matchResultReader.findByMember(meId)).willReturn(Optional.empty());

        PendingMatchResultResponse result = service.findPendingResult(userId).orElseThrow();

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.announcedAt()).isEqualTo(LocalDateTime.of(2026, 7, 10, 22, 0));
    }

    @Test
    @DisplayName("새 매칭으로 새 방(PENDING)에 배정되면 이전 방의 pending_result는 반환되지 않는다")
    void givenNewPendingRoom_whenFindPendingResult_thenEmpty() {
        Long newRoomId = 99L;
        RoomMember oldMember = new RoomMember(meId, roomId, userId, "MALE", null,
                LocalDateTime.of(2026, 7, 8, 9, 0));
        RoomMember newMember = new RoomMember(21L, newRoomId, userId, "MALE", null,
                LocalDateTime.of(2026, 7, 12, 22, 0));
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of(oldMember, newMember));
        // 최신 멤버십의 방(PENDING, startedAt 없음)만 조회된다
        given(roomReader.findById(newRoomId)).willReturn(Optional.of(
                new Room(newRoomId, RoomStatus.PENDING, LocalDate.of(2026, 7, 13), 3, null, null)));

        assertThat(service.findPendingResult(userId)).isEmpty();
    }

    @Test
    @DisplayName("참여한 방이 없으면 pending_result가 비어 있다")
    void givenNoMembership_whenFindPendingResult_thenEmpty() {
        given(roomMemberReader.findByUserId(userId)).willReturn(List.of());

        assertThat(service.findPendingResult(userId)).isEmpty();
    }
}
