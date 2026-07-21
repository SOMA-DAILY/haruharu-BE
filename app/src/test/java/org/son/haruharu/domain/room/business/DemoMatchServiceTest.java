package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.room.business.dto.DemoMatchResultResponse;
import org.son.haruharu.domain.room.implement.DemoRoomFinder;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.MatchResultWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DemoMatchServiceTest {

    @Mock DemoRoomFinder demoRoomFinder;
    @Mock RoomMemberReader roomMemberReader;
    @Mock MatchResultReader matchResultReader;
    @Mock MatchResultWriter matchResultWriter;
    @Mock ChatService chatService;
    @Mock CoinService coinService;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(ZonedDateTime.of(2026, 7, 22, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    DemoMatchService service;

    private static final Long USER_ID = 42L;
    private static final Long PARTNER_USER_ID = 101L;

    @BeforeEach
    void setUp() {
        service = new DemoMatchService(demoRoomFinder, roomMemberReader, matchResultReader, matchResultWriter,
                chatService, coinService, new ConsolationRewardProperties(5L), clock);
    }

    private Room demoRoom() {
        return new Room(7L, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 22), 3,
                LocalDateTime.of(2026, 7, 22, 9, 0), null, true, 3);
    }

    /** 본인(MALE) + 가상 남성 + 가상 여성 — 반대 성별 우선 선택을 검증하기 위한 구성. */
    private List<RoomMember> members() {
        return List.of(
                new RoomMember(70L, 7L, USER_ID, "MALE", null, LocalDateTime.now(clock)),
                new RoomMember(71L, 7L, 100L, "MALE", null, LocalDateTime.now(clock)),
                new RoomMember(72L, 7L, PARTNER_USER_ID, "FEMALE", null, LocalDateTime.now(clock)));
    }

    @Test
    @DisplayName("MATCHED면 반대 성별 가상 멤버와 성사 처리하고 실제 채팅방을 시드한다")
    void givenMatched_whenCreateResult_thenSeedsChatWithOppositeGenderPartner() {
        given(demoRoomFinder.require(USER_ID)).willReturn(demoRoom());
        given(roomMemberReader.getByRoomId(7L)).willReturn(members());
        given(matchResultReader.findByMember(70L)).willReturn(Optional.empty());
        given(matchResultWriter.save(any())).willAnswer(inv -> {
            MatchResult m = inv.getArgument(0);
            return new MatchResult(500L, m.memberAId(), m.memberBId(), m.matchedAt());
        });
        given(chatService.seedDemoChat(500L, USER_ID, PARTNER_USER_ID)).willReturn(900L);

        DemoMatchResultResponse result = service.createResult(USER_ID, DemoMatchOutcome.MATCHED);

        assertThat(result.roomId()).isEqualTo(7L);
        assertThat(result.outcome()).isEqualTo("MATCHED");
        assertThat(result.chatRoomId()).isEqualTo(900L);
        // 남성 본인 → 여성 가상 멤버(72L)와 성사
        ArgumentCaptor<MatchResult> captor = ArgumentCaptor.forClass(MatchResult.class);
        then(matchResultWriter).should().save(captor.capture());
        assertThat(List.of(captor.getValue().memberAId(), captor.getValue().memberBId()))
                .containsExactlyInAnyOrder(70L, 72L);
    }

    @Test
    @DisplayName("이미 성사된 상태면 매칭 결과를 새로 만들지 않고 재사용한다(멱등)")
    void givenAlreadyMatched_whenCreateResult_thenReusesExisting() {
        given(demoRoomFinder.require(USER_ID)).willReturn(demoRoom());
        given(roomMemberReader.getByRoomId(7L)).willReturn(members());
        given(matchResultReader.findByMember(70L))
                .willReturn(Optional.of(new MatchResult(500L, 70L, 72L, LocalDateTime.now(clock))));
        given(chatService.seedDemoChat(500L, USER_ID, PARTNER_USER_ID)).willReturn(900L);

        DemoMatchResultResponse result = service.createResult(USER_ID, DemoMatchOutcome.MATCHED);

        assertThat(result.chatRoomId()).isEqualTo(900L);
        then(matchResultWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("재사용 시 상대는 멤버 조회 순서가 아니라 저장된 매칭 결과에서 되짚는다")
    void givenReorderedMembers_whenCreateResultAgain_thenPartnerFollowsStoredMatch() {
        given(demoRoomFinder.require(USER_ID)).willReturn(demoRoom());
        // 멤버 조회 순서가 뒤바뀌어 반대 성별 우선 선택이 다른 사람을 고를 수 있는 상황
        List<RoomMember> reordered = List.of(
                new RoomMember(73L, 7L, 102L, "FEMALE", null, LocalDateTime.now(clock)),
                new RoomMember(70L, 7L, USER_ID, "MALE", null, LocalDateTime.now(clock)),
                new RoomMember(72L, 7L, PARTNER_USER_ID, "FEMALE", null, LocalDateTime.now(clock)));
        given(roomMemberReader.getByRoomId(7L)).willReturn(reordered);
        // 저장된 결과의 상대는 72L(PARTNER_USER_ID)
        given(matchResultReader.findByMember(70L))
                .willReturn(Optional.of(new MatchResult(500L, 70L, 72L, LocalDateTime.now(clock))));
        given(chatService.seedDemoChat(500L, USER_ID, PARTNER_USER_ID)).willReturn(900L);

        DemoMatchResultResponse result = service.createResult(USER_ID, DemoMatchOutcome.MATCHED);

        // 새로 고른 73L이 아니라 저장된 72L의 userId로 채팅이 시드돼야 한다
        assertThat(result.chatRoomId()).isEqualTo(900L);
        then(chatService).should().seedDemoChat(500L, USER_ID, PARTNER_USER_ID);
    }

    @Test
    @DisplayName("성사시킬 상대가 없는 방이면 404(DemoPartnerNotFoundException)")
    void givenNoPartner_whenCreateResult_thenThrows() {
        given(demoRoomFinder.require(USER_ID)).willReturn(demoRoom());
        given(roomMemberReader.getByRoomId(7L)).willReturn(
                List.of(new RoomMember(70L, 7L, USER_ID, "MALE", null, LocalDateTime.now(clock))));
        given(matchResultReader.findByMember(70L)).willReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.createResult(USER_ID, DemoMatchOutcome.MATCHED))
                .isInstanceOf(org.son.haruharu.domain.room.business.exception.DemoPartnerNotFoundException.class);
        then(matchResultWriter).should(never()).save(any());
        then(chatService).should(never()).seedDemoChat(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("FAILED면 위로 구름만 지급하고 채팅방은 만들지 않는다")
    void givenFailed_whenCreateResult_thenGrantsConsolationOnly() {
        given(demoRoomFinder.require(USER_ID)).willReturn(demoRoom());

        DemoMatchResultResponse result = service.createResult(USER_ID, DemoMatchOutcome.FAILED);

        assertThat(result.outcome()).isEqualTo("FAILED");
        assertThat(result.chatRoomId()).isNull();
        then(coinService).should().grantMatchConsolation(USER_ID, 7L, 5L);
        then(chatService).should(never()).seedDemoChat(anyLong(), anyLong(), anyLong());
        then(matchResultWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("리셋 정리는 멤버들의 채팅을 지운 뒤 매칭 결과를 하드삭제한다")
    void givenMatchedMembers_whenPurge_thenPurgesChatThenMatchResults() {
        given(matchResultReader.findByMember(70L))
                .willReturn(Optional.of(new MatchResult(500L, 70L, 72L, LocalDateTime.now(clock))));
        given(matchResultReader.findByMember(72L))
                .willReturn(Optional.of(new MatchResult(500L, 70L, 72L, LocalDateTime.now(clock))));

        service.purge(List.of(70L, 72L));

        // 같은 매칭 결과를 공유하는 두 멤버여도 채팅 정리는 1회만(distinct)
        then(chatService).should().purgeDemoChat(500L);
        then(matchResultWriter).should().hardDeleteByMemberIds(List.of(70L, 72L));
    }
}
