package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.FinalSelectionResponse;
import org.son.haruharu.domain.room.business.exception.AlreadySelectedException;
import org.son.haruharu.domain.room.business.exception.CannotSelectSelfException;
import org.son.haruharu.domain.room.business.exception.NotFinalSelectionDayException;
import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.FinalSelectionReader;
import org.son.haruharu.domain.room.implement.FinalSelectionWriter;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FinalSelectionServiceTest {

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock FinalSelectionReader finalSelectionReader;
    @Mock FinalSelectionWriter finalSelectionWriter;
    @Mock MatchResultWriter matchResultWriter;

    FinalSelectionService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final UUID roomId = UUID.randomUUID();
    private final Long userId = 1L;
    private final UUID meId = UUID.randomUUID();
    private final UUID selectedId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FinalSelectionService(roomReader, roomMemberReader, finalSelectionReader,
                finalSelectionWriter, matchResultWriter, clock);
    }

    private RoomMember member(UUID id, Long memberUserId, UUID memberRoomId) {
        return new RoomMember(id, memberRoomId, memberUserId, "MALE", null, LocalDateTime.now());
    }

    // currentDay는 startedAt에서 파생된다. clock=2026-06-26 19:00(KST) 기준 방날짜에서 (currentDay-1)일 전 09:00 앵커.
    private Room room(int currentDay) {
        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 26, 9, 0).minusDays(currentDay - 1L);
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, startedAt, null);
    }

    private void stubMeAndRoom(int currentDay) {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, userId, roomId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(currentDay)));
    }

    @Test
    @DisplayName("Day3에 상대가 아직 나를 선택하지 않았으면 저장만 하고 matched=false")
    void givenDay3AndNoMutual_whenSelect_thenSavedNotMatched() {
        stubMeAndRoom(3);
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);
        given(finalSelectionReader.findByRoomMemberId(selectedId)).willReturn(Optional.empty());

        FinalSelectionResponse result = service.select(roomId, selectedId, userId);

        assertThat(result.matched()).isFalse();
        then(finalSelectionWriter).should().save(any(FinalSelection.class));
        then(matchResultWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("상대도 나를 선택했으면 즉시 매칭되고 MatchResult는 canonical 정렬(a<b)로 저장된다")
    void givenMutual_whenSelect_thenMatchedAndCanonicalSaved() {
        stubMeAndRoom(3);
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);
        given(finalSelectionReader.findByRoomMemberId(selectedId))
                .willReturn(Optional.of(new FinalSelection(UUID.randomUUID(), selectedId, meId, LocalDateTime.now())));

        FinalSelectionResponse result = service.select(roomId, selectedId, userId);

        assertThat(result.matched()).isTrue();
        ArgumentCaptor<MatchResult> captor = ArgumentCaptor.forClass(MatchResult.class);
        then(matchResultWriter).should().save(captor.capture());
        MatchResult saved = captor.getValue();
        assertThat(saved.memberAId().compareTo(saved.memberBId())).isLessThan(0);
    }

    @Test
    @DisplayName("Day3 이전이면 NotFinalSelectionDayException이 발생한다")
    void givenBeforeDay3_whenSelect_thenThrows() {
        stubMeAndRoom(2);

        assertThatThrownBy(() -> service.select(roomId, selectedId, userId))
                .isInstanceOf(NotFinalSelectionDayException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("자기 자신을 선택하면 CannotSelectSelfException이 발생한다")
    void givenSelfSelection_whenSelect_thenThrows() {
        stubMeAndRoom(3);

        assertThatThrownBy(() -> service.select(roomId, meId, userId))
                .isInstanceOf(CannotSelectSelfException.class);
    }

    @Test
    @DisplayName("이미 최종선택했으면 AlreadySelectedException이 발생한다(1회 확정)")
    void givenAlreadySelected_whenSelect_thenThrows() {
        stubMeAndRoom(3);
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(true);

        assertThatThrownBy(() -> service.select(roomId, selectedId, userId))
                .isInstanceOf(AlreadySelectedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("다른 방 멤버를 선택하면 RoomMemberNotFoundException이 발생한다")
    void givenMemberInAnotherRoom_whenSelect_thenThrows() {
        stubMeAndRoom(3);
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, UUID.randomUUID()));

        assertThatThrownBy(() -> service.select(roomId, selectedId, userId))
                .isInstanceOf(RoomMemberNotFoundException.class);
    }
}
