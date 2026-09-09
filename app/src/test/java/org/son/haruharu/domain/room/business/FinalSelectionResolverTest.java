package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.room.business.event.MutualMatchEvent;
import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.FinalSelectionReader;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.MatchResultWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class FinalSelectionResolverTest {

    @Mock RoomMemberReader roomMemberReader;
    @Mock FinalSelectionReader finalSelectionReader;
    @Mock MatchResultReader matchResultReader;
    @Mock MatchResultWriter matchResultWriter;
    @Mock BlockReader blockReader;
    @Mock ApplicationEventPublisher eventPublisher;

    FinalSelectionResolver resolver;

    private static final Long ROOM = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 26, 22, 0);

    @BeforeEach
    void setUp() {
        resolver = new FinalSelectionResolver(roomMemberReader, finalSelectionReader,
                matchResultReader, matchResultWriter, blockReader, eventPublisher);
    }

    private RoomMember member(Long id, Long userId, String gender) {
        return new RoomMember(id, ROOM, userId, gender, null, null, LocalDateTime.now());
    }

    private FinalSelection choice(Long memberId, Long selectedMemberId) {
        return new FinalSelection(memberId * 10, memberId, selectedMemberId, NOW.minusHours(1));
    }

    @Test
    @DisplayName("서로 선택한 쌍은 canonical(a<b) MatchResult 1행으로 성사되고 MutualMatchEvent가 발행된다")
    void givenMutualPair_whenResolve_thenMatchesOnceCanonical() {
        // 멤버 1(user100)·2(user200)이 서로 선택
        given(roomMemberReader.getByRoomId(ROOM)).willReturn(List.of(
                member(1L, 100L, "MALE"), member(2L, 200L, "FEMALE")));
        given(finalSelectionReader.findByRoomMemberIds(List.of(1L, 2L))).willReturn(List.of(
                choice(1L, 2L), choice(2L, 1L)));
        given(matchResultReader.isMatched(anyLong(), anyLong())).willReturn(false);
        given(matchResultWriter.save(any())).willAnswer(inv -> {
            MatchResult m = inv.getArgument(0);
            return new MatchResult(500L, m.memberAId(), m.memberBId(), m.matchedAt());
        });

        int matched = resolver.resolveForRoom(ROOM, NOW);

        assertThat(matched).isEqualTo(1); // 쌍 1개, 중복 성사 없음
        ArgumentCaptor<MatchResult> captor = ArgumentCaptor.forClass(MatchResult.class);
        then(matchResultWriter).should().save(captor.capture());
        assertThat(captor.getValue().memberAId()).isLessThan(captor.getValue().memberBId()); // canonical
        ArgumentCaptor<MutualMatchEvent> eventCaptor = ArgumentCaptor.forClass(MutualMatchEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().matchResultId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("한쪽만 선택(짝사랑)한 경우는 성사되지 않는다")
    void givenOneSided_whenResolve_thenNoMatch() {
        given(roomMemberReader.getByRoomId(ROOM)).willReturn(List.of(
                member(1L, 100L, "MALE"), member(2L, 200L, "FEMALE")));
        given(finalSelectionReader.findByRoomMemberIds(List.of(1L, 2L))).willReturn(List.of(
                choice(1L, 2L))); // 2는 1을 안 고름

        int matched = resolver.resolveForRoom(ROOM, NOW);

        assertThat(matched).isZero();
        then(matchResultWriter).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("차단 관계인 상호선택 쌍은 성사시키지 않는다(#408 — 어느 쪽이 차단했든 매칭 불성립)")
    void givenBlockedMutualPair_whenResolve_thenNoMatch() {
        given(roomMemberReader.getByRoomId(ROOM)).willReturn(List.of(
                member(1L, 100L, "MALE"), member(2L, 200L, "FEMALE")));
        given(finalSelectionReader.findByRoomMemberIds(List.of(1L, 2L))).willReturn(List.of(
                choice(1L, 2L), choice(2L, 1L)));
        given(blockReader.isBlockedBetween(anyLong(), anyLong())).willReturn(true);

        int matched = resolver.resolveForRoom(ROOM, NOW);

        assertThat(matched).isZero();
        then(matchResultWriter).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("이미 성사된 쌍은 건너뛴다(멱등 — 재sweep 안전)")
    void givenAlreadyMatched_whenResolve_thenIdempotent() {
        given(roomMemberReader.getByRoomId(ROOM)).willReturn(List.of(
                member(1L, 100L, "MALE"), member(2L, 200L, "FEMALE")));
        given(finalSelectionReader.findByRoomMemberIds(List.of(1L, 2L))).willReturn(List.of(
                choice(1L, 2L), choice(2L, 1L)));
        given(matchResultReader.isMatched(anyLong(), anyLong())).willReturn(true); // 이미 성사됨

        int matched = resolver.resolveForRoom(ROOM, NOW);

        assertThat(matched).isZero();
        then(matchResultWriter).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("6인 방에서 두 쌍이 상호선택하면 두 쌍 모두 성사되고 짝사랑은 제외된다")
    void givenSixMembers_whenResolve_thenTwoPairsMatch() {
        // 1↔2 상호, 3↔4 상호, 5→6 짝사랑(6은 5 안 고름)
        given(roomMemberReader.getByRoomId(ROOM)).willReturn(List.of(
                member(1L, 100L, "MALE"), member(2L, 200L, "FEMALE"),
                member(3L, 300L, "MALE"), member(4L, 400L, "FEMALE"),
                member(5L, 500L, "MALE"), member(6L, 600L, "FEMALE")));
        given(finalSelectionReader.findByRoomMemberIds(List.of(1L, 2L, 3L, 4L, 5L, 6L))).willReturn(List.of(
                choice(1L, 2L), choice(2L, 1L), choice(3L, 4L), choice(4L, 3L), choice(5L, 6L)));
        given(matchResultReader.isMatched(anyLong(), anyLong())).willReturn(false);
        given(matchResultWriter.save(any())).willAnswer(inv -> {
            MatchResult m = inv.getArgument(0);
            return new MatchResult(999L, m.memberAId(), m.memberBId(), m.matchedAt());
        });

        int matched = resolver.resolveForRoom(ROOM, NOW);

        assertThat(matched).isEqualTo(2);
        then(matchResultWriter).should(org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    @DisplayName("멤버가 없으면 아무 것도 하지 않는다")
    void givenNoMembers_whenResolve_thenNoop() {
        given(roomMemberReader.getByRoomId(ROOM)).willReturn(List.of());
        lenient().when(finalSelectionReader.findByRoomMemberIds(any())).thenReturn(List.of());

        assertThat(resolver.resolveForRoom(ROOM, NOW)).isZero();
        then(matchResultWriter).should(never()).save(any());
    }
}
